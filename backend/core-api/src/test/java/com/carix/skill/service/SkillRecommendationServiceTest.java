package com.carix.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carix.config.IndexThresholdProperties;
import com.carix.index.enums.SkillDemandStatus;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.index.mvi.SkillDemandCalculator;
import com.carix.index.mvi.SkillDemandCalculator.SkillDemandResult;
import com.carix.index.mvi.SkillWeightCalculator;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingSkill;
import com.carix.jobposting.JobPostingSkillRepository;
import com.carix.market.MarketAggregateRepository;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.peergroup.PeerSalaryDistributionResolver.ResolvedPeerPostings;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.skill.domain.Skill;
import com.carix.skill.domain.UserSkill;
import com.carix.skill.dto.SkillRecommendationsResponse;
import com.carix.skill.dto.SkillRecommendationsResponse.SkillRecommendationItem;
import com.carix.skill.repository.SkillRepository;
import com.carix.skill.repository.UserSkillRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SkillRecommendationServiceTest {

  @Mock private CareerProfileRepository careerProfileRepository;
  @Mock private UserSkillRepository userSkillRepository;
  @Mock private SkillRepository skillRepository;
  @Mock private JobPostingSkillRepository jobPostingSkillRepository;
  @Mock private PeerSalaryDistributionResolver peerSalaryDistributionResolver;
  @Mock private PeerGroupFallbackResolver peerGroupFallbackResolver;
  @Mock private SkillDemandCalculator skillDemandCalculator;
  @Mock private MarketAggregateRepository marketAggregateRepository;
  @Mock private SkillWeightCalculator skillWeightCalculator;

  @InjectMocks private SkillRecommendationService service;

  private IndexThresholdProperties thresholds;
  private UUID userId;
  private CareerProfile profile;

  @BeforeEach
  void setUp() {
    thresholds = new IndexThresholdProperties();
    thresholds.setPeerGroupMinSampleSize(10);
    thresholds.setCoOccurrenceMinRate(0.20);
    ReflectionTestUtils.setField(service, "thresholds", thresholds);

    userId = UUID.randomUUID();
    profile =
        CareerProfile.builder()
            .userId(userId)
            .jobFamily("DEVELOPER")
            .yearsOfExperience(5)
            .region("서울 강남구")
            .build();
  }

  @Test
  @DisplayName("US-D6: 정상 케이스 - 동시출현율이 20% 이상인 경우 동시출현 근거가 노출된다")
  void getRecommendations_withHighCoOccurrence_returnsCoOccurrenceReason() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    // 보유 스킬: Spring Boot
    Skill springBoot =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Spring Boot")
            .category("BACKEND")
            .isCustom(false)
            .build();
    UserSkill userSkill = UserSkill.builder().userId(userId).skill(springBoot).build();
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    // 추천 대상 스킬: Kubernetes (미보유)
    Skill k8s =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Kubernetes")
            .category("DEVOPS")
            .isCustom(false)
            .build();

    // 동료집단 공고 20건 생성
    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);

      // Spring Boot는 10건(0~9)에 출현 (표본 >= 10 충족)
      if (i < 10) {
        jpsList.add(new JobPostingSkill(p, springBoot));
      }
      // Kubernetes는 8건(0~7)에 출현 -> Spring Boot와 동시출현 8건 / 10건 = 80% (>= 20%)
      if (i < 8) {
        jpsList.add(new JobPostingSkill(p, k8s));
      }
    }

    ResolvedPeerPostings resolvedPeer =
        new ResolvedPeerPostings(1, postings, "서울 전체 / 경력 MID / 회사규모 무관");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);

    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));
    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    // Kubernetes 트렌드: UP
    SkillDemandResult k8sTrend =
        SkillDemandResult.builder()
            .skillId(k8s.getId())
            .status(SkillDemandStatus.OK)
            .demandIndex(120.0)
            .build();
    when(skillDemandCalculator.calculate(eq(k8s.getId()), any(), any())).thenReturn(k8sTrend);

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.peerGroup().appliedConditions()).isEqualTo("서울 전체 / 경력 MID / 회사규모 무관");
    assertThat(response.recommendations()).hasSize(1);

    SkillRecommendationItem item = response.recommendations().get(0);
    assertThat(item.standardName()).isEqualTo("Kubernetes");
    assertThat(item.category()).isEqualTo("DEVOPS");
    assertThat(item.demandRate()).isEqualTo(0.40); // 8 / 20 = 0.4
    assertThat(item.growthTrend()).isEqualTo("UP");
    assertThat(item.coOccurrence()).isNotNull();
    assertThat(item.coOccurrence().withSkill()).isEqualTo("Spring Boot");
    assertThat(item.coOccurrence().rate()).isEqualTo(0.80);
    assertThat(item.reason()).isEqualTo("보유하신 Spring Boot 공고의 80%가 함께 요구합니다.");
  }

  @Test
  @DisplayName("프레임 행만 있는 달은 스킬 추천 기준월이 아니다 - ALL 행이 있는 최신 월을 쓴다")
  void frameOnlyMonthIsNotBaseMonth() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    // 보유 스킬: Spring Boot
    Skill springBoot =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Spring Boot")
            .category("BACKEND")
            .isCustom(false)
            .build();
    UserSkill userSkill = UserSkill.builder().userId(userId).skill(springBoot).build();
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    // 추천 대상 스킬: Kubernetes (미보유)
    Skill k8s =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Kubernetes")
            .category("DEVOPS")
            .isCustom(false)
            .build();

    // 동료집단 공고 20건 생성
    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);

      // Spring Boot는 10건(0~9)에 출현 (표본 >= 10 충족)
      if (i < 10) {
        jpsList.add(new JobPostingSkill(p, springBoot));
      }
      // Kubernetes는 8건(0~7)에 출현 -> Spring Boot와 동시출현 8건 / 10건 = 80% (>= 20%)
      if (i < 8) {
        jpsList.add(new JobPostingSkill(p, k8s));
      }
    }

    ResolvedPeerPostings resolvedPeer =
        new ResolvedPeerPostings(1, postings, "서울 전체 / 경력 MID / 회사규모 무관");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);

    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202610"));
    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), eq("202610"), eq(profile)))
        .thenReturn(mockResolvedPeer);

    // Kubernetes 트렌드: UP
    SkillDemandResult k8sTrend =
        SkillDemandResult.builder()
            .skillId(k8s.getId())
            .status(SkillDemandStatus.OK)
            .demandIndex(120.0)
            .build();
    when(skillDemandCalculator.calculate(eq(k8s.getId()), any(), any())).thenReturn(k8sTrend);

    service.getRecommendations(userId);

    verify(peerGroupFallbackResolver).resolve("ALL", "202610", profile);
  }

  @Test
  @DisplayName("US-D6: 동시출현율이 20% 미만인 경우 coOccurrence는 null이고 동료집단 수요율 문구로 대체된다")
  void getRecommendations_withLowCoOccurrence_fallsBackToDemandRateReason() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    Skill springBoot =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Spring Boot")
            .category("BACKEND")
            .isCustom(false)
            .build();
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(UserSkill.builder().userId(userId).skill(springBoot).build()));

    Skill ts =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("TypeScript")
            .category("FRONTEND")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);

      // Spring Boot는 10건(0~9) 출현
      if (i < 10) {
        jpsList.add(new JobPostingSkill(p, springBoot));
      }
      // TypeScript는 5건(9~13) 출현 -> 동시출현 1건(i=9) / 10건 = 10% (< 20%)
      if (i >= 9 && i < 14) {
        jpsList.add(new JobPostingSkill(p, ts));
      }
    }

    ResolvedPeerPostings resolvedPeer =
        new ResolvedPeerPostings(1, postings, "서울 전체 / 경력 MID / 회사규모 무관");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);

    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));
    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    SkillDemandResult tsTrend =
        SkillDemandResult.builder()
            .skillId(ts.getId())
            .status(SkillDemandStatus.OK)
            .demandIndex(98.0)
            .build();
    when(skillDemandCalculator.calculate(eq(ts.getId()), any(), any())).thenReturn(tsTrend);

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.recommendations()).hasSize(1);
    SkillRecommendationItem item = response.recommendations().get(0);
    assertThat(item.standardName()).isEqualTo("TypeScript");
    assertThat(item.growthTrend()).isEqualTo("STABLE");
    assertThat(item.coOccurrence()).isNull();
    assertThat(item.reason()).isEqualTo(SkillRecommendationService.DEFAULT_DEMAND_REASON);
  }

  @Test
  @DisplayName("US-D6: 기준 스킬의 동료집단 표본이 10건 미만이면 coOccurrence는 null이고 수요율 문구가 노출된다")
  void getRecommendations_baseSkillUnderSampled_nullCoOccurrence() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    Skill rust =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Rust")
            .category("BACKEND")
            .isCustom(false)
            .build();
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(UserSkill.builder().userId(userId).skill(rust).build()));

    Skill go =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Go")
            .category("BACKEND")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);

      // Rust는 단 3건만 출현 (< 10)
      if (i < 3) {
        jpsList.add(new JobPostingSkill(p, rust));
      }
      if (i < 8) {
        jpsList.add(new JobPostingSkill(p, go));
      }
    }

    ResolvedPeerPostings resolvedPeer =
        new ResolvedPeerPostings(1, postings, "서울 전체 / 경력 MID / 회사규모 무관");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);

    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));
    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.recommendations()).hasSize(1);
    SkillRecommendationItem item = response.recommendations().get(0);
    assertThat(item.standardName()).isEqualTo("Go");
    assertThat(item.coOccurrence()).isNull();
    assertThat(item.reason()).isEqualTo(SkillRecommendationService.DEFAULT_DEMAND_REASON);
  }

  @Test
  @DisplayName("US-D6: 커스텀 스킬은 추천 후보 및 기준 스킬 양쪽에서 모두 제외된다")
  void getRecommendations_customSkillsExcluded() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    // 사용자의 커스텀 스킬
    Skill customOwned =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Custom Framework")
            .category("CUSTOM")
            .isCustom(true)
            .build();
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(UserSkill.builder().userId(userId).skill(customOwned).build()));

    // 후보 중 커스텀 스킬 및 표준 스킬
    Skill customCandidate =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Company Inhouse Tool")
            .category("CUSTOM")
            .isCustom(true)
            .build();
    Skill standardCandidate =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Docker")
            .category("DEVOPS")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      jpsList.add(new JobPostingSkill(p, customCandidate));
      if (i < 12) {
        jpsList.add(new JobPostingSkill(p, standardCandidate));
      }
    }

    ResolvedPeerPostings resolvedPeer =
        new ResolvedPeerPostings(1, postings, "서울 전체 / 경력 MID / 회사규모 무관");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);

    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));
    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    // 커스텀 스킬은 후보에서 제외되어 Docker만 추천되어야 함
    assertThat(response.recommendations()).hasSize(1);
    assertThat(response.recommendations().get(0).standardName()).isEqualTo("Docker");
    // 보유 스킬이 커스텀 스킬뿐이므로 기준 스킬 부재 -> coOccurrence는 null
    assertThat(response.recommendations().get(0).coOccurrence()).isNull();
  }

  @Test
  @DisplayName("US-D6: 동료집단 표본이 최소 기준(10건) 미달이면 빈 배열을 반환한다")
  void getRecommendations_underSampledPeerGroup_returnsEmptyList() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    List<JobPosting> sparsePostings =
        List.of(
            JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build(),
            JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build());

    ResolvedPeerPostings resolvedPeer =
        new ResolvedPeerPostings(5, sparsePostings, "서울 전체 / 경력 MID / 회사규모 무관");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.recommendations()).isEmpty();
    assertThat(response.peerGroup().appliedConditions()).isEqualTo("서울 전체 / 경력 MID / 회사규모 무관");
  }

  @Test
  @DisplayName(
      "성장 트렌드(growthTrend)의 다양한 분기(DOWN, STABLE, NEW_OBSERVATION, NO_RECENT_DEMAND, 예외 시 기본값)를 검증한다")
  void getRecommendations_growthTrend_allBranches() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    Skill skillDown =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillDown")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillStable =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillStable")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillNew =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillNew")
            .category("TEST")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      if (i < 15) jpsList.add(new JobPostingSkill(p, skillDown));
      if (i < 12) jpsList.add(new JobPostingSkill(p, skillStable));
      if (i < 10) jpsList.add(new JobPostingSkill(p, skillNew));
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    // 1. DOWN (demandIndex < 95.0)
    when(skillDemandCalculator.calculate(eq(skillDown.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(90.0).build());

    // 2. STABLE (demandIndex between 95.0 and 105.0)
    when(skillDemandCalculator.calculate(eq(skillStable.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(100.0).build());

    // 3. NEW_OBSERVATION -> UP
    when(skillDemandCalculator.calculate(eq(skillNew.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder()
                .status(SkillDemandStatus.NEW_OBSERVATION)
                .demandIndex(0.0)
                .build());

    SkillRecommendationsResponse response = service.getRecommendations(userId);
    assertThat(response.recommendations()).hasSize(3);

    // b23: Tier 2 신규 유입/표본부족 폴백(SkillNew: UP) -> Tier 3 보합(SkillStable: STABLE, 100.0) -> Tier 3
    // 하락(SkillDown: DOWN, 90.0)
    assertThat(response.recommendations().get(0).growthTrend()).isEqualTo("UP");
    assertThat(response.recommendations().get(1).growthTrend()).isEqualTo("STABLE");
    assertThat(response.recommendations().get(2).growthTrend()).isEqualTo("DOWN");
  }

  @Test
  @DisplayName("성장 트렌드 산출 중 NO_RECENT_DEMAND는 DOWN으로, 예외 발생 시에는 기본값 STABLE로 처리된다")
  void getRecommendations_growthTrend_noRecentDemandAndException() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    Skill skillNoRecent =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillNoRecent")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillError =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillError")
            .category("TEST")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      jpsList.add(new JobPostingSkill(p, skillNoRecent));
      jpsList.add(new JobPostingSkill(p, skillError));
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    when(skillDemandCalculator.calculate(eq(skillNoRecent.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder()
                .status(SkillDemandStatus.NO_RECENT_DEMAND)
                .demandIndex(0.0)
                .build());
    when(skillDemandCalculator.calculate(eq(skillError.getId()), any(), any()))
        .thenThrow(new RuntimeException("MVI 산출기 일시 오류"));

    SkillRecommendationsResponse response = service.getRecommendations(userId);
    assertThat(response.recommendations()).hasSize(2);

    SkillRecommendationItem itemNoRecent =
        response.recommendations().stream()
            .filter(r -> r.standardName().equals("SkillNoRecent"))
            .findFirst()
            .orElseThrow();
    assertThat(itemNoRecent.growthTrend()).isEqualTo("DOWN");

    SkillRecommendationItem itemError =
        response.recommendations().stream()
            .filter(r -> r.standardName().equals("SkillError"))
            .findFirst()
            .orElseThrow();
    assertThat(itemError.growthTrend()).isEqualTo("STABLE");
  }

  @Test
  @DisplayName("limit 파라미터 지정 시 요청한 개수만큼 추천 스킬을 반환한다")
  void getRecommendations_withCustomLimit() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    List<Skill> skills = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      skills.add(
          Skill.builder()
              .id(UUID.randomUUID())
              .standardName("Skill-" + i)
              .category("TEST")
              .isCustom(false)
              .build());
    }

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      for (Skill s : skills) {
        jpsList.add(new JobPostingSkill(p, s));
      }
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    SkillRecommendationsResponse response = service.getRecommendations(userId, 2);
    assertThat(response.recommendations()).hasSize(2);
  }

  @Test
  @DisplayName("b23: 최근 3개월 대 이전 12개월 수요 상승률이 높은 스킬이 원시 수요율이 낮더라도 최우선 추천된다")
  void b23_risingSkillsRankedHigherThanFlatOrDecliningEvenWithLowerDemandRate() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    // 대형 스킬 (수요율 높음: 18/20, 하지만 demandIndex 90.0으로 하락세)
    Skill giantDeclining =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("GiantDeclining")
            .category("BACKEND")
            .isCustom(false)
            .build();
    // 라이징 스킬 (수요율 낮음: 5/20, 하지만 demandIndex 180.0으로 급상승)
    Skill risingFast =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("RisingFast")
            .category("BACKEND")
            .isCustom(false)
            .build();
    // 중간 라이징 스킬 (수요율 중간: 8/20, demandIndex 130.0으로 상승)
    Skill risingModerate =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("RisingModerate")
            .category("BACKEND")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      if (i < 18) jpsList.add(new JobPostingSkill(p, giantDeclining));
      if (i < 8) jpsList.add(new JobPostingSkill(p, risingModerate));
      if (i < 5) jpsList.add(new JobPostingSkill(p, risingFast));
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    when(skillDemandCalculator.calculate(eq(giantDeclining.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(90.0).build());
    when(skillDemandCalculator.calculate(eq(risingFast.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(180.0).build());
    when(skillDemandCalculator.calculate(eq(risingModerate.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(130.0).build());

    SkillRecommendationsResponse response = service.getRecommendations(userId, 3);
    assertThat(response.recommendations()).hasSize(3);

    // 1위: RisingFast (demandIndex 180.0)
    assertThat(response.recommendations().get(0).standardName()).isEqualTo("RisingFast");
    assertThat(response.recommendations().get(0).growthTrend()).isEqualTo("UP");

    // 2위: RisingModerate (demandIndex 130.0)
    assertThat(response.recommendations().get(1).standardName()).isEqualTo("RisingModerate");
    assertThat(response.recommendations().get(1).growthTrend()).isEqualTo("UP");

    // 3위: GiantDeclining (demandIndex 90.0)
    assertThat(response.recommendations().get(2).standardName()).isEqualTo("GiantDeclining");
    assertThat(response.recommendations().get(2).growthTrend()).isEqualTo("DOWN");
  }

  @Test
  @DisplayName("b23: 원시 수요율 순위로는 limit 밖이었던 상승 스킬이 랭킹 전에 평가되어 상위 limit 내로 추천된다")
  void b23_risingSkillOutsideTopLimitByRawDemandRateIsDiscoveredAndRecommended() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    // 4개의 스킬: skill1(15건), skill2(12건), skill3(10건) -> 기존 limit 2였다면 skill4(5건)는 탈락했어야 함
    Skill skill1 =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Skill1")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skill2 =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Skill2")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skill3 =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Skill3")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillRising =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillRising")
            .category("TEST")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      if (i < 15) jpsList.add(new JobPostingSkill(p, skill1));
      if (i < 12) jpsList.add(new JobPostingSkill(p, skill2));
      if (i < 10) jpsList.add(new JobPostingSkill(p, skill3));
      if (i < 5) jpsList.add(new JobPostingSkill(p, skillRising));
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    // skill1, skill2, skill3는 보합 (demandIndex 100.0)
    when(skillDemandCalculator.calculate(eq(skill1.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(100.0).build());
    when(skillDemandCalculator.calculate(eq(skill2.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(100.0).build());
    when(skillDemandCalculator.calculate(eq(skill3.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(100.0).build());
    // skillRising은 급상승 (demandIndex 150.0)
    when(skillDemandCalculator.calculate(eq(skillRising.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(150.0).build());

    // limit을 2개로 요청
    SkillRecommendationsResponse response = service.getRecommendations(userId, 2);
    assertThat(response.recommendations()).hasSize(2);

    // 원시 수요율 4위였던 SkillRising이 1위로 올라와 2위 안에 반드시 포함되어야 함
    assertThat(response.recommendations().get(0).standardName()).isEqualTo("SkillRising");
    assertThat(response.recommendations().get(0).growthTrend()).isEqualTo("UP");
  }

  @Test
  @DisplayName("b23: 수집 이력이 미달되거나 표본 부족(INSUFFICIENT_SAMPLE)인 경우 수요율 순위(b9)로 완전히 폴백한다")
  void b23_fallbackToDemandRateOrderWhenGrowthSampleIsInsufficient() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    Skill skillA =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillA")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillB =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillB")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillC =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillC")
            .category("TEST")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      if (i < 15) jpsList.add(new JobPostingSkill(p, skillA));
      if (i < 10) jpsList.add(new JobPostingSkill(p, skillB));
      if (i < 5) jpsList.add(new JobPostingSkill(p, skillC));
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    // 모든 스킬의 상승률 산출이 INSUFFICIENT_SAMPLE (15개월 이력 부족 등)
    when(skillDemandCalculator.calculate(any(), any(), any()))
        .thenReturn(
            SkillDemandResult.builder()
                .status(SkillDemandStatus.INSUFFICIENT_SAMPLE)
                .demandIndex(0.0)
                .build());

    SkillRecommendationsResponse response = service.getRecommendations(userId, 3);
    assertThat(response.recommendations()).hasSize(3);

    // 수요율 순서: SkillA(15건) -> SkillB(10건) -> SkillC(5건)
    assertThat(response.recommendations().get(0).standardName()).isEqualTo("SkillA");
    assertThat(response.recommendations().get(1).standardName()).isEqualTo("SkillB");
    assertThat(response.recommendations().get(2).standardName()).isEqualTo("SkillC");
    // 표본 부족 시 트렌드는 STABLE
    assertThat(response.recommendations().get(0).growthTrend()).isEqualTo("STABLE");
  }

  @Test
  @DisplayName(
      "b23: 동일 Tier 내 동률 타이브레이킹(demandIndex -> demandRate -> postingCount -> 이름) 및 Tier 3 순서를 검증한다")
  void b23_tieBreakingAndTier3Ordering() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    // 동일 demandIndex(120.0), 다른 수요율 (15건 vs 10건)
    Skill skillT1A =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillT1A")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillT1B =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillT1B")
            .category("TEST")
            .isCustom(false)
            .build();
    // 동일 demandIndex(120.0), 동일 수요율(8건), 다른 이름 (Beta vs Alpha)
    Skill skillT1Beta =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Beta")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillT1Alpha =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Alpha")
            .category("TEST")
            .isCustom(false)
            .build();

    // Tier 3: 보합(100.0) vs 하락(80.0) vs NO_RECENT_DEMAND(0.0)
    Skill skillStable =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillStable")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillDown =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillDown")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillNoRecent =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("SkillNoRecent")
            .category("TEST")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      if (i < 15) jpsList.add(new JobPostingSkill(p, skillT1A));
      if (i < 10) jpsList.add(new JobPostingSkill(p, skillT1B));
      if (i < 8) {
        jpsList.add(new JobPostingSkill(p, skillT1Beta));
        jpsList.add(new JobPostingSkill(p, skillT1Alpha));
      }
      if (i < 6) jpsList.add(new JobPostingSkill(p, skillStable));
      if (i < 4) jpsList.add(new JobPostingSkill(p, skillDown));
      if (i < 2) jpsList.add(new JobPostingSkill(p, skillNoRecent));
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    when(skillDemandCalculator.calculate(eq(skillT1A.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(120.0).build());
    when(skillDemandCalculator.calculate(eq(skillT1B.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(120.0).build());
    when(skillDemandCalculator.calculate(eq(skillT1Beta.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(120.0).build());
    when(skillDemandCalculator.calculate(eq(skillT1Alpha.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(120.0).build());

    when(skillDemandCalculator.calculate(eq(skillStable.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(100.0).build());
    when(skillDemandCalculator.calculate(eq(skillDown.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(80.0).build());
    when(skillDemandCalculator.calculate(eq(skillNoRecent.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder()
                .status(SkillDemandStatus.NO_RECENT_DEMAND)
                .demandIndex(0.0)
                .build());

    SkillRecommendationsResponse response = service.getRecommendations(userId, 7);
    assertThat(response.recommendations()).hasSize(7);

    // Tier 1: 120.0 내림차순 -> 수요율 높은 순(15 > 10) -> 동률 시 알파벳 순(Alpha > Beta)
    assertThat(response.recommendations().get(0).standardName()).isEqualTo("SkillT1A");
    assertThat(response.recommendations().get(1).standardName()).isEqualTo("SkillT1B");
    assertThat(response.recommendations().get(2).standardName()).isEqualTo("Alpha");
    assertThat(response.recommendations().get(3).standardName()).isEqualTo("Beta");

    // Tier 3: 지수 높은 순(100.0 > 80.0 > 0.0)
    assertThat(response.recommendations().get(4).standardName()).isEqualTo("SkillStable");
    assertThat(response.recommendations().get(5).standardName()).isEqualTo("SkillDown");
    assertThat(response.recommendations().get(6).standardName()).isEqualTo("SkillNoRecent");
  }

  @Test
  @DisplayName("프로필 미설정 사용자의 경우 안내 문구와 함께 빈 목록을 반환한다")
  void getRecommendations_withNoProfile_returnsEmpty() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.recommendations()).isEmpty();
    assertThat(response.peerGroup().appliedConditions()).isEqualTo("동료집단 미설정");
  }

  @Test
  @DisplayName("thresholds 가 null 인 경우 기본 임계값을 사용하여 정상 동작한다")
  void getRecommendations_withNullThresholds_usesDefaultThresholds() {
    ReflectionTestUtils.setField(service, "thresholds", null);
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(peerSalaryDistributionResolver.resolvePostings(profile))
        .thenReturn(new ResolvedPeerPostings(1, List.of(), "조건"));

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.recommendations()).isEmpty();
    // 복구
    ReflectionTestUtils.setField(service, "thresholds", thresholds);
  }

  @Test
  @DisplayName("동료집단 공고 목록이 null 이거나 최소 표본 미만인 경우 빈 목록을 반환한다")
  void getRecommendations_withNullOrInsufficientPeerPostings_returnsEmpty() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(peerSalaryDistributionResolver.resolvePostings(profile))
        .thenReturn(new ResolvedPeerPostings(1, null, "조건"));

    SkillRecommendationsResponse response = service.getRecommendations(userId);
    assertThat(response.recommendations()).isEmpty();
  }

  @Test
  @DisplayName("limit 인자 경계값(null, 음수, 0, 30 초과) 정규화 검증")
  void getRecommendations_normalizesLimit() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(peerSalaryDistributionResolver.resolvePostings(profile))
        .thenReturn(new ResolvedPeerPostings(1, List.of(), "조건"));

    // null limit -> 기본 10으로 동작하여 에러 없이 반환
    assertThat(service.getRecommendations(userId, null).recommendations()).isEmpty();
    // 음수 limit -> 기본 10으로 보정
    assertThat(service.getRecommendations(userId, -5).recommendations()).isEmpty();
    // 0 limit -> 기본 10으로 보정
    assertThat(service.getRecommendations(userId, 0).recommendations()).isEmpty();
    // 50 limit -> MAX_LIMIT 30으로 보정
    assertThat(service.getRecommendations(userId, 50).recommendations()).isEmpty();
  }

  @Test
  @DisplayName("사용자가 공고의 모든 스킬을 이미 보유하고 있어 미보유 후보가 없는 경우 빈 목록 반환")
  void getRecommendations_whenAllSkillsOwned_returnsEmpty() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    Skill ownedSkill =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Java")
            .category("BACKEND")
            .isCustom(false)
            .build();
    UserSkill userSkill = UserSkill.builder().userId(userId).skill(ownedSkill).build();
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
    List<JobPosting> postings = new ArrayList<>();
    for (int i = 0; i < 15; i++) postings.add(p);

    when(peerSalaryDistributionResolver.resolvePostings(profile))
        .thenReturn(new ResolvedPeerPostings(1, postings, "조건"));
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection()))
        .thenReturn(List.of(new JobPostingSkill(p, ownedSkill)));

    SkillRecommendationsResponse response = service.getRecommendations(userId);
    assertThat(response.recommendations()).isEmpty();
  }

  @Test
  @DisplayName("커스텀 스킬 및 null 매핑 예외 처리 분기 검증")
  void getRecommendations_handlesCustomAndNullSkillsInJobPostingAndUserSkills() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    Skill normalOwned =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill customOwned =
        Skill.builder().id(UUID.randomUUID()).standardName("MyCustom").isCustom(true).build();
    Skill higherOwned =
        Skill.builder().id(UUID.randomUUID()).standardName("Spring").isCustom(false).build();

    // 사용자가 일반 스킬(Java, Spring), 커스텀 스킬(MyCustom), null 스킬을 보유
    UserSkill us1 = UserSkill.builder().userId(userId).skill(normalOwned).build();
    UserSkill us2 = UserSkill.builder().userId(userId).skill(customOwned).build();
    UserSkill us3 = UserSkill.builder().userId(userId).skill(null).build();
    UserSkill us4 = UserSkill.builder().userId(userId).skill(higherOwned).build();
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(us1, us2, us3, us4));

    Skill candidateStandard =
        Skill.builder().id(UUID.randomUUID()).standardName("Docker").isCustom(false).build();
    Skill candidateCustom =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("CustomCandidate")
            .isCustom(true)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      // countSkillPostings: higherOwned (15번), normalOwned (5번) -> maxBasePostings 갱신 분기 검증
      jpsList.add(new JobPostingSkill(p, higherOwned));
      if (i < 5) jpsList.add(new JobPostingSkill(p, normalOwned));
      // candidate: candidateStandard (10번), candidateCustom (10번 - 제외되어야 함)
      if (i < 10) {
        jpsList.add(new JobPostingSkill(p, candidateStandard));
        jpsList.add(new JobPostingSkill(p, candidateCustom));
      }
      // null JobPosting 및 null Skill 분기 검증
      jpsList.add(new JobPostingSkill(null, candidateStandard));
      jpsList.add(new JobPostingSkill(p, null));
    }

    when(peerSalaryDistributionResolver.resolvePostings(profile))
        .thenReturn(new ResolvedPeerPostings(1, postings, "조건"));
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    // 최신 기준연월 조회가 empty 인 경우 currentYm 폴백 검증
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.empty());

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    when(skillDemandCalculator.calculate(eq(candidateStandard.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(115.0).build());

    SkillRecommendationsResponse response = service.getRecommendations(userId);
    assertThat(response.recommendations()).hasSize(1);
    assertThat(response.recommendations().get(0).standardName()).isEqualTo("Docker");
    assertThat(response.recommendations().get(0).growthTrend()).isEqualTo("UP");
  }

  @Test
  @DisplayName("랭킹 정렬 세부 동률 처리 및 지표 분기(Tier 1, 2, 3 상세 동률, NEW_OBSERVATION 등) 완전 검증")
  void getRecommendations_comprehensiveComparatorBranches() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    // Tier 1 스킬들 (Index 120.0 동률, demandRate 동률, postingCount 가 다른 경우)
    Skill t1A = Skill.builder().id(UUID.randomUUID()).standardName("T1A").isCustom(false).build();
    Skill t1B = Skill.builder().id(UUID.randomUUID()).standardName("T1B").isCustom(false).build();

    // Tier 2 스킬들 (NEW_OBSERVATION, NO_RECENT_COVERAGE, demandRate 동률, postingCount 동률 후 이름순)
    Skill t2A = Skill.builder().id(UUID.randomUUID()).standardName("T2A").isCustom(false).build();
    Skill t2B = Skill.builder().id(UUID.randomUUID()).standardName("T2B").isCustom(false).build();

    // Tier 3 스킬들 (Index 동률, demandRate 다른 경우, demandRate 동률에 postingCount 다른 경우)
    Skill t3A = Skill.builder().id(UUID.randomUUID()).standardName("T3A").isCustom(false).build();
    Skill t3B = Skill.builder().id(UUID.randomUUID()).standardName("T3B").isCustom(false).build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      if (i < 10) jpsList.add(new JobPostingSkill(p, t1A));
      if (i < 8) jpsList.add(new JobPostingSkill(p, t1B));
      if (i < 6) jpsList.add(new JobPostingSkill(p, t2A));
      if (i < 6) jpsList.add(new JobPostingSkill(p, t2B));
      if (i < 4) jpsList.add(new JobPostingSkill(p, t3A));
      if (i < 3) jpsList.add(new JobPostingSkill(p, t3B));
    }

    when(peerSalaryDistributionResolver.resolvePostings(profile))
        .thenReturn(new ResolvedPeerPostings(1, postings, "조건"));
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    // t1A, t1B: 120.0
    when(skillDemandCalculator.calculate(eq(t1A.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(120.0).build());
    when(skillDemandCalculator.calculate(eq(t1B.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(120.0).build());

    // t2A: NEW_OBSERVATION, t2B: NO_RECENT_COVERAGE (Tier 2 폴백)
    when(skillDemandCalculator.calculate(eq(t2A.getId()), any(), any()))
        .thenReturn(SkillDemandResult.builder().status(SkillDemandStatus.NEW_OBSERVATION).build());
    when(skillDemandCalculator.calculate(eq(t2B.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.NO_RECENT_COVERAGE).build());

    // t3A: 80.0, demandRate 4/20 = 0.20, DOWN 트렌드
    when(skillDemandCalculator.calculate(eq(t3A.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(80.0).build());
    // t3B: 80.0, demandRate 3/20 = 0.15, DOWN 트렌드
    when(skillDemandCalculator.calculate(eq(t3B.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder().status(SkillDemandStatus.OK).demandIndex(80.0).build());

    SkillRecommendationsResponse response = service.getRecommendations(userId, 6);
    assertThat(response.recommendations()).hasSize(6);

    // Tier 1: t1A(count 10) > t1B(count 8)
    assertThat(response.recommendations().get(0).standardName()).isEqualTo("T1A");
    assertThat(response.recommendations().get(1).standardName()).isEqualTo("T1B");

    // Tier 2: rate 동률(6), count 동률(6) -> 알파벳 순(T2A > T2B)
    assertThat(response.recommendations().get(2).standardName()).isEqualTo("T2A");
    assertThat(response.recommendations().get(3).standardName()).isEqualTo("T2B");

    // Tier 3: index 80.0 동률 -> rate 큰 순(t3A(0.20) > t3B(0.15))
    assertThat(response.recommendations().get(4).standardName()).isEqualTo("T3A");
    assertThat(response.recommendations().get(5).standardName()).isEqualTo("T3B");
    assertThat(response.recommendations().get(4).growthTrend()).isEqualTo("DOWN");
  }

  // ---------- 추천 후보 정리 ----------

  private Skill skill(String name, String category) {
    return Skill.builder()
        .id(UUID.randomUUID())
        .standardName(name)
        .category(category)
        .isCustom(false)
        .build();
  }

  /** 공고 20건. 보유 스킬별 출현 수와 후보별 출현 수를 받아 동료집단을 만든다. */
  private void givenPeer(Map<Skill, Integer> appearances) {
    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> links = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      for (Map.Entry<Skill, Integer> entry : appearances.entrySet()) {
        if (i < entry.getValue()) {
          links.add(new JobPostingSkill(p, entry.getKey()));
        }
      }
    }
    when(peerSalaryDistributionResolver.resolvePostings(profile))
        .thenReturn(new ResolvedPeerPostings(1, postings, "전국 / 경력 전체"));
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(links);
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202609"));
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class));
  }

  @Test
  @DisplayName("수집 태그(SKILL·JOB_CATEGORY·INDUSTRY) 항목은 추천하지 않는다")
  void 수집_태그_스킬은_추천하지_않는다() {
    // 회귀: 수집 태그가 표준 스킬과 같은 컬럼에 들어와 Java 5년차에게 Ai·백엔드·API·CI·CD 가 추천됐다.
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    Skill java = skill("Java", "BACKEND");
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(UserSkill.builder().userId(userId).skill(java).build()));
    Skill backendTag = skill("백엔드", "SKILL");
    Skill jobTag = skill("서버개발자", "JOB_CATEGORY");
    Skill python = skill("Python", "BACKEND");
    givenPeer(Map.of(java, 12, backendTag, 15, jobTag, 14, python, 6));
    when(skillDemandCalculator.calculate(eq(python.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder()
                .skillId(python.getId())
                .status(SkillDemandStatus.OK)
                .demandIndex(110.0)
                .build());

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.recommendations())
        .extracting(SkillRecommendationItem::standardName)
        .containsExactly("Python");
  }

  @Test
  @DisplayName("동시출현 기준 스킬은 공고 수가 아니라 내 스킬 가중치 최상위다")
  void 기준_스킬은_가중치_최상위다() {
    // 회귀: 공고 수가 가장 많은 보유 스킬(AWS)을 기준으로 삼아 Java 개발자에게 "AWS 공고의 N%" 근거가 나왔다.
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    Skill java = skill("Java", "BACKEND");
    Skill aws = skill("AWS", "DEVOPS");
    UserSkill javaSkill = UserSkill.builder().userId(userId).skill(java).build();
    UserSkill awsSkill = UserSkill.builder().userId(userId).skill(aws).build();
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(javaSkill, awsSkill));
    Skill kotlin = skill("Kotlin", "BACKEND");
    givenPeer(Map.of(java, 10, aws, 18, kotlin, 8));
    when(skillWeightCalculator.calculateWeights(any(), eq("202609"), any()))
        .thenReturn(
            Map.of(java.getId(), new BigDecimal("0.7"), aws.getId(), new BigDecimal("0.3")));
    when(skillDemandCalculator.calculate(eq(kotlin.getId()), any(), any()))
        .thenReturn(
            SkillDemandResult.builder()
                .skillId(kotlin.getId())
                .status(SkillDemandStatus.OK)
                .demandIndex(110.0)
                .build());

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.recommendations().get(0).coOccurrence().withSkill()).isEqualTo("Java");
  }
}
