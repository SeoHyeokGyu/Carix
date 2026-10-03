package com.carix.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carix.config.IndexThresholdProperties;
import com.carix.index.enums.SkillDemandStatus;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.index.mvi.SkillDemandCalculator;
import com.carix.index.mvi.SkillDemandCalculator.SkillDemandResult;
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
import java.util.ArrayList;
import java.util.List;
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

    when(marketAggregateRepository.findLatestAvailableYearMonth(any()))
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

    when(marketAggregateRepository.findLatestAvailableYearMonth(any()))
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
    assertThat(item.reason()).isEqualTo("동료집단 내 공고의 25.0%에서 요구되는 핵심 스택입니다.");
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

    when(marketAggregateRepository.findLatestAvailableYearMonth(any()))
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
    assertThat(item.reason()).contains("동료집단 내 공고의 40.0%에서 요구되는 핵심 스택입니다.");
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

    when(marketAggregateRepository.findLatestAvailableYearMonth(any()))
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
  @DisplayName("US-D6: 프로필이 존재하지 않는 경우 빈 배열을 반환한다")
  void getRecommendations_profileNotFound_returnsEmpty() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());

    SkillRecommendationsResponse response = service.getRecommendations(userId);

    assertThat(response.recommendations()).isEmpty();
    assertThat(response.peerGroup().appliedConditions()).isEqualTo("동료집단 미설정");
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
    when(marketAggregateRepository.findLatestAvailableYearMonth(any()))
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

    assertThat(response.recommendations().get(0).growthTrend()).isEqualTo("DOWN");
    assertThat(response.recommendations().get(1).growthTrend()).isEqualTo("STABLE");
    assertThat(response.recommendations().get(2).growthTrend()).isEqualTo("UP");
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
    when(marketAggregateRepository.findLatestAvailableYearMonth(any()))
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
  @DisplayName("수요율과 공고 수가 동일할 때 스킬 표준명(standardName) 오름차순으로 타이브레이킹된다")
  void getRecommendations_tieBreakingSortByStandardName() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    Skill skillZ =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Z-Skill")
            .category("TEST")
            .isCustom(false)
            .build();
    Skill skillA =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("A-Skill")
            .category("TEST")
            .isCustom(false)
            .build();

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      jpsList.add(new JobPostingSkill(p, skillZ));
      jpsList.add(new JobPostingSkill(p, skillA));
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);
    when(marketAggregateRepository.findLatestAvailableYearMonth(any()))
        .thenReturn(Optional.of("202607"));

    PeerGroupFallbackResolver.ResolvedPeerGroup mockResolvedPeer =
        mock(PeerGroupFallbackResolver.ResolvedPeerGroup.class);
    when(peerGroupFallbackResolver.resolve(eq("ALL"), any(), eq(profile)))
        .thenReturn(mockResolvedPeer);

    SkillRecommendationsResponse response = service.getRecommendations(userId);
    assertThat(response.recommendations()).hasSize(2);
    // 동일 출현 횟수일 때 standardName 사전순으로 A-Skill이 첫 번째
    assertThat(response.recommendations().get(0).standardName()).isEqualTo("A-Skill");
    assertThat(response.recommendations().get(1).standardName()).isEqualTo("Z-Skill");
  }

  @Test
  @DisplayName("동료집단 공고의 스킬을 사용자가 이미 전부 보유하고 있는 경우 빈 추천 목록을 반환한다")
  void getRecommendations_allSkillsOwned_returnsEmpty() {
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    Skill ownedSkill =
        Skill.builder()
            .id(UUID.randomUUID())
            .standardName("Java")
            .category("BACKEND")
            .isCustom(false)
            .build();
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(UserSkill.builder().userId(userId).skill(ownedSkill).build()));

    List<JobPosting> postings = new ArrayList<>();
    List<JobPostingSkill> jpsList = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      JobPosting p = JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build();
      postings.add(p);
      jpsList.add(new JobPostingSkill(p, ownedSkill));
    }

    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, postings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);
    when(jobPostingSkillRepository.findByJobPostingIdIn(anyCollection())).thenReturn(jpsList);

    SkillRecommendationsResponse response = service.getRecommendations(userId);
    assertThat(response.recommendations()).isEmpty();
  }

  @Test
  @DisplayName("IndexThresholdProperties가 주입되지 않아도 기본 임계값(표본 10, 동시출현 20%)으로 정상 동작한다")
  void getRecommendations_nullThresholds_usesDefaults() {
    ReflectionTestUtils.setField(service, "thresholds", null);
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

    List<JobPosting> sparsePostings =
        List.of(JobPosting.builder().id(UUID.randomUUID()).jobFamily("DEVELOPER").build());
    ResolvedPeerPostings resolvedPeer = new ResolvedPeerPostings(1, sparsePostings, "조건");
    when(peerSalaryDistributionResolver.resolvePostings(profile)).thenReturn(resolvedPeer);

    SkillRecommendationsResponse response = service.getRecommendations(userId);
    assertThat(response.recommendations()).isEmpty();
  }
}
