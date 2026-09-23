package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.carix.index.dto.MviResult;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.WageBaseline;
import com.carix.index.enums.IndexStatus;
import com.carix.index.enums.SkillDemandStatus;
import com.carix.index.enums.SkillExclusionReason;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.skill.domain.Skill;
import com.carix.skill.domain.UserSkill;
import com.carix.skill.repository.UserSkillRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketValueIndexServiceTest {

  @Mock private CareerProfileRepository careerProfileRepository;
  @Mock private UserSkillRepository userSkillRepository;
  @Mock private PeerGroupFallbackResolver peerGroupFallbackResolver;
  @Mock private SkillDemandCalculator skillDemandCalculator;
  @Mock private SkillWeightCalculator skillWeightCalculator;
  @Mock private WageBaselineResolver wageBaselineResolver;
  @Mock private com.carix.market.MarketAggregateRepository marketAggregateRepository;

  @InjectMocks private MarketValueIndexServiceImpl service;

  @Test
  @DisplayName("보유 스킬이 없는 경우 INSUFFICIENT_SAMPLE 상태와 null 값을 반환해야 한다")
  void testNoSkillsReturnsInsufficientSample() {
    UUID userId = UUID.randomUUID();
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    MviResult result = service.calculate(userId, "202607");

    assertThat(result.value()).isNull();
    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
  }

  @Test
  @DisplayName("정상 계산: 원천별 스킬 수요 지수와 가중치를 결합하여 MviResult를 산출해야 한다")
  void testSuccessfulMviCalculation() {
    // Given
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    Skill skill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    UserSkill userSkill =
        UserSkill.builder()
            .skill(skill)
            .monthsOfExperience(24)
            .lastUsedDate(LocalDate.of(2026, 7, 1))
            .build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup resolvedGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source("JOBKOREA")
            .successful(true)
            .sampleCount(50)
            .fallbackLevel(1)
            .coverage(new SourceCoverage("JOBKOREA", 1, 50, true, "조건", BigDecimal.ZERO))
            .build();

    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any())).thenReturn(resolvedGroup);

    SkillDemandCalculator.SkillDemandResult demandResult =
        SkillDemandCalculator.SkillDemandResult.builder()
            .skillId(skill.getId())
            .demandIndex(120.0)
            .status(SkillDemandStatus.OK)
            .build();

    when(skillDemandCalculator.calculateDemandIndex(
            any(), eq(skill.getId()), any(), eq(baseYearMonth)))
        .thenReturn(demandResult);

    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(skill.getId(), BigDecimal.valueOf(1.0)));

    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    // When
    MviResult result = service.calculate(userId, baseYearMonth);

    // Then
    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.value()).isNotNull();
    assertThat(result.value().doubleValue()).isEqualTo(120.0);
    assertThat(result.wageBaseline()).isNotNull();
    assertThat(result.wageBaseline().medianGrossSalary()).isEqualTo(65000000L);
  }

  @Test
  @DisplayName("최근 수요 없음 스킬은 제외가 아니라 0 으로 가중합에 들어가고 별도 목록으로 구분된다")
  void testNoRecentDemandSkillIsZeroWeightedNotExcluded() {
    // Arrange
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    Skill active =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill stale =
        Skill.builder().id(UUID.randomUUID()).standardName("COBOL").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(userSkillOf(active), userSkillOf(stale)));

    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(
            PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
                .source("JOBKOREA")
                .successful(true)
                .sampleCount(50)
                .fallbackLevel(1)
                .coverage(new SourceCoverage("JOBKOREA", 1, 50, true, "조건", BigDecimal.ZERO))
                .build());

    when(skillDemandCalculator.calculateDemandIndex(
            any(), eq(active.getId()), any(), eq(baseYearMonth)))
        .thenReturn(
            SkillDemandCalculator.SkillDemandResult.builder()
                .skillId(active.getId())
                .demandIndex(120.0)
                .status(SkillDemandStatus.OK)
                .build());
    when(skillDemandCalculator.calculateDemandIndex(
            any(), eq(stale.getId()), any(), eq(baseYearMonth)))
        .thenReturn(
            SkillDemandCalculator.SkillDemandResult.builder()
                .skillId(stale.getId())
                .demandIndex(0.0)
                .status(SkillDemandStatus.NO_RECENT_DEMAND)
                .build());

    // 두 스킬 모두 가중치를 받는다 — 최근 수요 없음은 제외가 아니기 때문이다.
    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(
            Map.of(
                active.getId(), BigDecimal.valueOf(0.5),
                stale.getId(), BigDecimal.valueOf(0.5)));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    // Act
    MviResult result = service.calculate(userId, baseYearMonth);

    // Assert
    assertThat(result.excludedSkills()).isEmpty();
    assertThat(result.zeroDemandSkills())
        .singleElement()
        .satisfies(
            noted -> {
              assertThat(noted.skillId()).isEqualTo(stale.getId());
              assertThat(noted.standardName()).isEqualTo("COBOL");
              assertThat(noted.reason()).isEqualTo(SkillExclusionReason.NO_RECENT_DEMAND);
            });

    // 0.5 × 120 + 0.5 × 0 = 60. 제외였다면 남은 스킬로 재정규화돼 120 이 나온다.
    assertThat(result.value()).isEqualByComparingTo("60.0");
  }

  @Test
  @DisplayName("모든 원천에서 표본이 미달인 스킬은 표본 부족으로 제외된다")
  void testSkillUnderSampledInEverySourceIsExcluded() {
    UUID userId = UUID.randomUUID();
    String ym = "202607";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    Skill active =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill scarce =
        Skill.builder().id(UUID.randomUUID()).standardName("Rust").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(userSkillOf(active), userSkillOf(scarce)));
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));

    when(skillDemandCalculator.calculateDemandIndex(any(), eq(active.getId()), any(), eq(ym)))
        .thenReturn(demand(active.getId(), 120.0, SkillDemandStatus.OK));
    when(skillDemandCalculator.calculateDemandIndex(any(), eq(scarce.getId()), any(), eq(ym)))
        .thenReturn(demand(scarce.getId(), 0.0, SkillDemandStatus.INSUFFICIENT_SAMPLE));

    // 제외된 스킬은 가중치 산출에서도 빠진다 — 실제 계산기와 같은 동작을 흉내낸다.
    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(active.getId(), BigDecimal.valueOf(1.0)));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, ym);

    assertThat(result.zeroDemandSkills()).isEmpty();
    assertThat(result.excludedSkills())
        .singleElement()
        .satisfies(
            excluded -> {
              assertThat(excluded.standardName()).isEqualTo("Rust");
              assertThat(excluded.reason()).isEqualTo(SkillExclusionReason.INSUFFICIENT_SAMPLE);
            });
    assertThat(result.value()).isEqualByComparingTo("120.0");
  }

  @Test
  @DisplayName("유효 가중치가 있어도 모든 원천이 산출 불가면 MVI 는 0 이 아니라 null 이다")
  void testEverySourceUncomputableYieldsNullNotZero() {
    UUID userId = UUID.randomUUID();
    String ym = "202604";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    Skill onlySkill =
        Skill.builder().id(UUID.randomUUID()).standardName("Spring Boot").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkillOf(onlySkill)));
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));

    // 최근 창에 동료집단 집계가 없어 두 원천 모두 판정 불가다 (운영 202604 형태).
    when(skillDemandCalculator.calculateDemandIndex(any(), eq(onlySkill.getId()), any(), eq(ym)))
        .thenReturn(demand(onlySkill.getId(), 0.0, SkillDemandStatus.NO_RECENT_COVERAGE));

    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(onlySkill.getId(), BigDecimal.valueOf(1.0)));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202603"));

    MviResult result = service.calculate(userId, ym);

    assertThat(result.value()).isNull();
    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.zeroDemandSkills()).isEmpty();
    // 사유가 표본 부족이면 화면이 "스킬을 더 넣으라" 고 안내한다. 수집 공백은 그렇게 해소되지 않는다.
    assertThat(result.excludedSkills())
        .singleElement()
        .satisfies(
            excluded ->
                assertThat(excluded.reason()).isEqualTo(SkillExclusionReason.NO_RECENT_COVERAGE));
  }

  @Test
  @DisplayName("한 원천은 집계 공백이고 다른 원천은 표본 미달이면 제외 사유는 표본 부족이다")
  void testMixedDropReasonsPrefersInsufficientSample() {
    UUID userId = UUID.randomUUID();
    String ym = "202604";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    Skill onlySkill =
        Skill.builder().id(UUID.randomUUID()).standardName("Spring Boot").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkillOf(onlySkill)));
    when(peerGroupFallbackResolver.resolveForSource(eq("JOBKOREA"), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));
    when(peerGroupFallbackResolver.resolveForSource(eq("SARAMIN"), any(), any()))
        .thenReturn(resolvedGroup("SARAMIN", 50));

    when(skillDemandCalculator.calculateDemandIndex(
            eq("JOBKOREA"), eq(onlySkill.getId()), any(), eq(ym)))
        .thenReturn(demand(onlySkill.getId(), 0.0, SkillDemandStatus.NO_RECENT_COVERAGE));
    when(skillDemandCalculator.calculateDemandIndex(
            eq("SARAMIN"), eq(onlySkill.getId()), any(), eq(ym)))
        .thenReturn(demand(onlySkill.getId(), 0.0, SkillDemandStatus.INSUFFICIENT_SAMPLE));

    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(onlySkill.getId(), BigDecimal.valueOf(1.0)));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202603"));

    MviResult result = service.calculate(userId, ym);

    // 집계가 있던 원천에서 표본이 모자랐다는 뜻이라 그쪽이 더 정확한 설명이다.
    assertThat(result.excludedSkills())
        .singleElement()
        .satisfies(
            excluded ->
                assertThat(excluded.reason()).isEqualTo(SkillExclusionReason.INSUFFICIENT_SAMPLE));
  }

  @Test
  @DisplayName("한 원천에서만 표본이 미달이면 제외하지 않고 그 원천의 가중치만 재정규화한다")
  void testSkillUnderSampledInOneSourceIsKept() {
    UUID userId = UUID.randomUUID();
    String ym = "202607";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    Skill active =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill partial =
        Skill.builder().id(UUID.randomUUID()).standardName("Kotlin").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(userSkillOf(active), userSkillOf(partial)));
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));

    when(skillDemandCalculator.calculateDemandIndex(any(), eq(active.getId()), any(), eq(ym)))
        .thenReturn(demand(active.getId(), 120.0, SkillDemandStatus.OK));
    when(skillDemandCalculator.calculateDemandIndex(
            eq("JOBKOREA"), eq(partial.getId()), any(), eq(ym)))
        .thenReturn(demand(partial.getId(), 80.0, SkillDemandStatus.OK));
    when(skillDemandCalculator.calculateDemandIndex(
            eq("SARAMIN"), eq(partial.getId()), any(), eq(ym)))
        .thenReturn(demand(partial.getId(), 0.0, SkillDemandStatus.INSUFFICIENT_SAMPLE));

    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(
            Map.of(
                active.getId(), BigDecimal.valueOf(0.5),
                partial.getId(), BigDecimal.valueOf(0.5)));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, ym);

    // JOBKOREA: 0.5×120 + 0.5×80 = 100 (가중치 합 1.0)
    // SARAMIN: Kotlin 이 빠져 0.5×120 을 0.5 로 재정규화 = 120
    // 표본수가 같으므로 (100 + 120) / 2 = 110. 재정규화가 없으면 SARAMIN 이 60 이 되어 80 이 나온다.
    assertThat(result.excludedSkills()).isEmpty();
    assertThat(result.value()).isEqualByComparingTo("110.0");
  }

  @Test
  @DisplayName("커스텀 스킬만 등록된 경우 모든 스킬이 제외되어 INSUFFICIENT_SAMPLE 및 제외 사유를 반환해야 한다")
  void testAllCustomSkillsExcludedReturnsInsufficientSample() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";

    CareerProfile profile = new CareerProfile(userId);
    Skill customSkill =
        Skill.builder().id(UUID.randomUUID()).standardName("MyTool").isCustom(true).build();
    UserSkill userSkill =
        UserSkill.builder()
            .skill(customSkill)
            .monthsOfExperience(12)
            .lastUsedDate(LocalDate.of(2026, 7, 1))
            .build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup resolvedGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source("JOBKOREA")
            .successful(true)
            .sampleCount(30)
            .fallbackLevel(0)
            .coverage(new SourceCoverage("JOBKOREA", 0, 30, true, "조건", BigDecimal.ZERO))
            .build();

    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any())).thenReturn(resolvedGroup);
    when(skillWeightCalculator.calculateWeights(any(), any(), any())).thenReturn(Map.of());
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(50000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, baseYearMonth);

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.value()).isNull();
    assertThat(result.excludedSkills()).hasSize(1);
    assertThat(result.excludedSkills().get(0).standardName()).isEqualTo("MyTool");
    assertThat(result.excludedSkills().get(0).reason())
        .isEqualTo(SkillExclusionReason.CUSTOM_SKILL_NO_STATS);
  }

  @Test
  @DisplayName("모든 원천의 표본 수가 부족(N < 10)한 경우 INSUFFICIENT_SAMPLE 및 임금 기준선을 반환해야 한다")
  void testAllSourcesInsufficientSamplesReturnsStatus() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";

    CareerProfile profile = new CareerProfile(userId);
    Skill skill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    UserSkill userSkill =
        UserSkill.builder()
            .skill(skill)
            .monthsOfExperience(12)
            .lastUsedDate(LocalDate.of(2026, 7, 1))
            .build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup lowSampleGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source("JOBKOREA")
            .successful(false)
            .sampleCount(3)
            .fallbackLevel(4)
            .coverage(new SourceCoverage("JOBKOREA", 4, 3, false, "조건", BigDecimal.ZERO))
            .build();

    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(lowSampleGroup);
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(55000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, baseYearMonth);

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.value()).isNull();
    assertThat(result.wageBaseline()).isNotNull();
    assertThat(result.wageBaseline().medianGrossSalary()).isEqualTo(55000000L);
  }

  @Test
  @DisplayName("getSkillDemandDetails - userId null 또는 스킬 목록 없음")
  void testGetSkillDemandDetails_empty() {
    assertThat(service.getSkillDemandDetails(null, "202607")).isEmpty();

    UUID userId = UUID.randomUUID();
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of());

    assertThat(service.getSkillDemandDetails(userId, "202607")).isEmpty();
  }

  @Test
  @DisplayName("getSkillDemandDetails - 표준 스킬 및 커스텀 스킬 혼합 조회")
  void testGetSkillDemandDetails_success() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";

    Skill standardSkill = Skill.createStandard("Spring Boot", "BACKEND");
    Skill customSkill = Skill.createCustom("MyTool", "TOOL", userId);
    UserSkill standardUserSkill = userSkillOf(standardSkill);
    UserSkill customUserSkill = userSkillOf(customSkill);

    when(careerProfileRepository.findByUserId(userId))
        .thenReturn(Optional.of(new CareerProfile(userId)));
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(standardUserSkill, customUserSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup jk = resolvedGroup("JOBKOREA", 100);
    PeerGroupFallbackResolver.ResolvedPeerGroup sm = resolvedGroup("SARAMIN", 100);
    when(peerGroupFallbackResolver.resolveForSource(eq("JOBKOREA"), any(), eq(baseYearMonth)))
        .thenReturn(jk);
    when(peerGroupFallbackResolver.resolveForSource(eq("SARAMIN"), any(), eq(baseYearMonth)))
        .thenReturn(sm);

    when(skillWeightCalculator.calculateWeights(any(), eq(baseYearMonth), any()))
        .thenReturn(Map.of(standardSkill.getId(), BigDecimal.valueOf(1.0)));

    SkillDemandCalculator.SkillDemandResult demandRes =
        SkillDemandCalculator.SkillDemandResult.builder()
            .skillId(standardSkill.getId())
            .demandIndex(110.0)
            .recentDemandRate(0.35)
            .baselineDemandRate(0.30)
            .status(SkillDemandStatus.OK)
            .build();

    when(skillDemandCalculator.calculateDemandIndex(
            any(), eq(standardSkill.getId()), any(), eq(baseYearMonth)))
        .thenReturn(demandRes);

    var details = service.getSkillDemandDetails(userId, baseYearMonth);

    assertThat(details).hasSize(2);
    var stdDetail =
        details.stream()
            .filter(d -> d.getSkillId().equals(standardSkill.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(stdDetail.getStandardName()).isEqualTo("Spring Boot");
    assertThat(stdDetail.getStatus()).isEqualTo("OK");
    assertThat(stdDetail.getSources()).hasSize(2);

    var custDetail =
        details.stream()
            .filter(d -> d.getSkillId().equals(customSkill.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(custDetail.getStatus()).isEqualTo("CUSTOM_SKILL_NO_STATS");
  }

  @Test
  @DisplayName("calculate - userId null 시 NOT_AVAILABLE 반환")
  void testCalculateNullUserId() {
    MviResult res = service.calculate(null, "202607");
    assertThat(res.value()).isNull();
    assertThat(res.status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
  }

  @Test
  @DisplayName("calculate - 커스텀 스킬 등으로 인해 모든 스킬이 제외되어 유효 가중치가 없으면 INSUFFICIENT_SAMPLE 반환")
  void testCalculateAllSkillsExcluded() {
    UUID userId = UUID.randomUUID();
    Skill customSkill = Skill.createCustom("SpecialTool", "ETC", userId);
    UserSkill userSkill = userSkillOf(customSkill);

    when(careerProfileRepository.findByUserId(userId))
        .thenReturn(Optional.of(new CareerProfile(userId)));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup jk = resolvedGroup("JOBKOREA", 50);
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any())).thenReturn(jk);
    when(skillWeightCalculator.calculateWeights(any(), any(), any())).thenReturn(Map.of());
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(50000000L, "초과급여 제외", "202606"));

    MviResult res = service.calculate(userId, "202607");
    assertThat(res.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(res.value()).isNull();
  }

  @Test
  @DisplayName("calculate - CareerProfile이 DB에 없더라도 기본 프로필을 생성하여 정상 계산 진행")
  void testCalculateWithDefaultProfileWhenNotFound() {
    UUID userId = UUID.randomUUID();
    Skill skill = Skill.createStandard("Python", "DATA");
    UserSkill userSkill = userSkillOf(skill);

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup jk = resolvedGroup("JOBKOREA", 50);
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any())).thenReturn(jk);

    SkillDemandCalculator.SkillDemandResult demandRes =
        SkillDemandCalculator.SkillDemandResult.builder()
            .skillId(skill.getId())
            .demandIndex(100.0)
            .status(SkillDemandStatus.OK)
            .build();
    when(skillDemandCalculator.calculateDemandIndex(any(), eq(skill.getId()), any(), any()))
        .thenReturn(demandRes);
    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(skill.getId(), BigDecimal.valueOf(1.0)));

    MviResult res = service.calculate(userId, "202607");
    assertThat(res.status()).isEqualTo(IndexStatus.OK);
    assertThat(res.value()).isEqualTo(BigDecimal.valueOf(100.0).setScale(1));
  }

  @Test
  @DisplayName("calculate - TotalPostings가 사전에 조회되면 오버로딩된 5인자 calculateDemandIndex를 호출하여 처리한다")
  void testCalculateWithPrecalculatedTotalPostings() {
    UUID userId = UUID.randomUUID();
    Skill skill = Skill.createStandard("Java", "BACKEND");
    UserSkill userSkill = userSkillOf(skill);

    when(careerProfileRepository.findByUserId(userId))
        .thenReturn(Optional.of(new CareerProfile(userId)));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup jk = resolvedGroup("JOBKOREA", 50);
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any())).thenReturn(jk);

    SkillDemandCalculator.TotalPostings totals = new SkillDemandCalculator.TotalPostings(100, 400);
    when(skillDemandCalculator.calculateTotalPostings(any(), any(), any())).thenReturn(totals);

    SkillDemandCalculator.SkillDemandResult demandRes =
        SkillDemandCalculator.SkillDemandResult.builder()
            .skillId(skill.getId())
            .demandIndex(150.0)
            .status(SkillDemandStatus.OK)
            .build();
    when(skillDemandCalculator.calculateDemandIndex(
            any(), eq(skill.getId()), any(), any(), eq(totals)))
        .thenReturn(demandRes);
    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(skill.getId(), BigDecimal.valueOf(1.0)));

    MviResult res = service.calculate(userId, "202607");
    assertThat(res.status()).isEqualTo(IndexStatus.OK);
    assertThat(res.value()).isEqualTo(BigDecimal.valueOf(150.0).setScale(1));
  }

  @Test
  @DisplayName(
      "getSkillDemandDetails - TotalPostings가 사전에 조회되면 5인자 calculateDemandIndex를 호출하여 상세 집계한다")
  void testGetSkillDemandDetailsWithPrecalculatedTotalPostings() {
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";
    Skill skill = Skill.createStandard("Java", "BACKEND");
    UserSkill userSkill = userSkillOf(skill);

    when(careerProfileRepository.findByUserId(userId))
        .thenReturn(Optional.of(new CareerProfile(userId)));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));

    PeerGroupFallbackResolver.ResolvedPeerGroup jk = resolvedGroup("JOBKOREA", 100);
    PeerGroupFallbackResolver.ResolvedPeerGroup sm = resolvedGroup("SARAMIN", 100);
    when(peerGroupFallbackResolver.resolveForSource(eq("JOBKOREA"), any(), eq(baseYearMonth)))
        .thenReturn(jk);
    when(peerGroupFallbackResolver.resolveForSource(eq("SARAMIN"), any(), eq(baseYearMonth)))
        .thenReturn(sm);

    SkillDemandCalculator.TotalPostings totals = new SkillDemandCalculator.TotalPostings(100, 400);
    when(skillDemandCalculator.calculateTotalPostings(any(), any(), any())).thenReturn(totals);

    when(skillWeightCalculator.calculateWeights(any(), eq(baseYearMonth), any()))
        .thenReturn(Map.of(skill.getId(), BigDecimal.valueOf(1.0)));

    SkillDemandCalculator.SkillDemandResult demandRes =
        SkillDemandCalculator.SkillDemandResult.builder()
            .skillId(skill.getId())
            .demandIndex(120.0)
            .recentDemandRate(0.3)
            .baselineDemandRate(0.25)
            .status(SkillDemandStatus.OK)
            .build();

    when(skillDemandCalculator.calculateDemandIndex(
            any(), eq(skill.getId()), any(), eq(baseYearMonth), eq(totals)))
        .thenReturn(demandRes);

    var details = service.getSkillDemandDetails(userId, baseYearMonth);

    assertThat(details).hasSize(1);
    assertThat(details.get(0).getDemandIndex()).isEqualTo(120.0);
    assertThat(details.get(0).getSources()).hasSize(2);
  }

  @Test
  @DisplayName("calculate - 사용자 프로필 조회 중 예외 발생 시 RuntimeException으로 래핑하여 던진다")
  void testCalculateThrowsWrappedExceptionWhenUserLoadFails() {
    UUID userId = UUID.randomUUID();
    when(careerProfileRepository.findByUserId(userId))
        .thenThrow(new IllegalStateException("DB 연결 실패"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.calculate(userId, "202607"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("사용자 데이터 로드 실패");
  }

  @Test
  @DisplayName("getSkillDemandDetails - 사용자 데이터 조회 실패 시 RuntimeException으로 래핑하여 던진다")
  void testGetSkillDemandDetailsThrowsWrappedExceptionWhenLoadFails() {
    UUID userId = UUID.randomUUID();
    when(careerProfileRepository.findByUserId(userId))
        .thenThrow(new IllegalStateException("DB 연결 실패"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.getSkillDemandDetails(userId, "202607"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("스킬 상세 수요 데이터 로드 실패");
  }

  private static PeerGroupFallbackResolver.ResolvedPeerGroup resolvedGroup(
      String source, int sampleCount) {
    return PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
        .source(source)
        .successful(true)
        .sampleCount(sampleCount)
        .fallbackLevel(1)
        .coverage(new SourceCoverage(source, 1, sampleCount, true, "조건", BigDecimal.ZERO))
        .build();
  }

  private static SkillDemandCalculator.SkillDemandResult demand(
      UUID skillId, double index, SkillDemandStatus status) {
    return SkillDemandCalculator.SkillDemandResult.builder()
        .skillId(skillId)
        .demandIndex(index)
        .status(status)
        .build();
  }

  @Test
  @DisplayName("당월 데이터가 없을 때 직전 유효 월로 fallback하여 MVI를 정상 산출해야 한다")
  void testFallbackToLatestAvailableYearMonth() {
    UUID userId = UUID.randomUUID();
    String requestedYm = "202609";
    String latestAvailableYm = "202608";

    when(marketAggregateRepository.findLatestAvailableYearMonth(requestedYm))
        .thenReturn(Optional.of(latestAvailableYm));

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    Skill skill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    UserSkill userSkill = userSkillOf(skill);

    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkill));
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(wageBaselineResolver.resolveBaseline(profile))
        .thenReturn(new WageBaseline(60_000_000L, "초과급여 제외, 상여 포함", latestAvailableYm));

    // fallback된 202608로 완화 및 스킬 수요 조회
    PeerGroupFallbackResolver.ResolvedPeerGroup peerJobkorea = resolvedGroup("JOBKOREA", 100);
    PeerGroupFallbackResolver.ResolvedPeerGroup peerSaramin = resolvedGroup("SARAMIN", 200);
    when(peerGroupFallbackResolver.resolveForSource(
            eq("JOBKOREA"), eq(profile), eq(latestAvailableYm)))
        .thenReturn(peerJobkorea);
    when(peerGroupFallbackResolver.resolveForSource(
            eq("SARAMIN"), eq(profile), eq(latestAvailableYm)))
        .thenReturn(peerSaramin);

    when(skillDemandCalculator.calculateDemandIndex(
            eq("JOBKOREA"), eq(skill.getId()), any(), eq(latestAvailableYm)))
        .thenReturn(demand(skill.getId(), 105.0, SkillDemandStatus.OK));
    when(skillDemandCalculator.calculateDemandIndex(
            eq("SARAMIN"), eq(skill.getId()), any(), eq(latestAvailableYm)))
        .thenReturn(demand(skill.getId(), 110.0, SkillDemandStatus.OK));

    when(skillWeightCalculator.calculateWeights(any(), eq(latestAvailableYm), any()))
        .thenReturn(Map.of(skill.getId(), BigDecimal.ONE));

    MviResult result = service.calculate(userId, requestedYm);

    assertThat(result.value()).isNotNull();
    assertThat(result.baseYearMonth()).isEqualTo(requestedYm);
    assertThat(result.aggregateAsOf()).isEqualTo(LocalDate.of(2026, 8, 31));
    assertThat(result.status()).isEqualTo(IndexStatus.OK);
  }

  private static UserSkill userSkillOf(Skill skill) {
    return UserSkill.builder()
        .skill(skill)
        .monthsOfExperience(24)
        .lastUsedDate(LocalDate.of(2026, 7, 1))
        .build();
  }
}
