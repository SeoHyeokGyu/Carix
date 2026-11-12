package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.carix.index.dto.ExcludedSkill;
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
  @DisplayName("정상 계산: 원천별 스킬 수요 지수와 가중치를 결합하여 MviResult를 산출해야 한다")
  void testSuccessfulMviCalculation() {
    // Given
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

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
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

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
  @DisplayName("스킬이 전부 최근 수요 없음이면 가중합 0 을 값으로 내지 않는다")
  void testAllSkillsZeroDemandYieldsNoValue() {
    // 기준 100 지수에서 0 은 도메인 정의서가 금지한 임의 수치다. 운영 스냅샷에 OK + 0.00 이 22행 남은 계열이다.
    UUID userId = UUID.randomUUID();
    String baseYearMonth = "202607";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

    Skill stale =
        Skill.builder().id(UUID.randomUUID()).standardName("COBOL").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkillOf(stale)));

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
            any(), eq(stale.getId()), any(), eq(baseYearMonth)))
        .thenReturn(
            SkillDemandCalculator.SkillDemandResult.builder()
                .skillId(stale.getId())
                .demandIndex(0.0)
                .status(SkillDemandStatus.NO_RECENT_DEMAND)
                .build());

    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(stale.getId(), BigDecimal.ONE));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, baseYearMonth);

    assertThat(result.value()).isNull();
    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.zeroDemandSkills()).singleElement().isNotNull();
  }

  @Test
  @DisplayName("유효 가중치가 있어도 모든 원천이 산출 불가면 MVI 는 0 이 아니라 null 이다")
  void testEverySourceUncomputableYieldsNullNotZero() {
    UUID userId = UUID.randomUUID();
    String ym = "202604";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

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
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

    Skill onlySkill =
        Skill.builder().id(UUID.randomUUID()).standardName("Spring Boot").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkillOf(onlySkill)));
    when(peerGroupFallbackResolver.resolveForSource(eq("JOBKOREA"), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));
    when(peerGroupFallbackResolver.resolveForSource(eq("SARAMIN"), any(), any()))
        .thenReturn(resolvedGroup("SARAMIN", 50));
    stubWantedAndJumpitUnsuccessful();

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
  @DisplayName("표준오차는 스킬별 최근·이전 창 건수로 낸다 — 원천은 표본 수로 가중 결합한다")
  void testStandardErrorFromSkillCounts() {
    UUID userId = UUID.randomUUID();
    String ym = "202609";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);
    Skill java = Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkillOf(java)));
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));
    stubWantedAndJumpitUnsuccessful();
    when(skillDemandCalculator.calculateDemandIndex(any(), eq(java.getId()), any(), eq(ym)))
        .thenReturn(
            SkillDemandCalculator.SkillDemandResult.builder()
                .skillId(java.getId())
                .demandIndex(120.0)
                .status(SkillDemandStatus.OK)
                .recentSkillPostings(25)
                .baselineSkillPostings(100)
                .build());
    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(java.getId(), BigDecimal.ONE));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, ym);

    // 원천별 분산 = 120² × (1/25 + 1/100) = 720. 두 원천 표본이 같아 가중치 ½씩: 0.25×720 + 0.25×720 = 360 → √360 ≈
    // 19.0
    assertThat(result.value()).isEqualByComparingTo("120.0");
    assertThat(result.standardError()).isEqualByComparingTo("19.0");
  }

  @Test
  @DisplayName("한 원천에서만 신규 관측이면 제외하지 않고 그 스킬은 지수를 낸 원천끼리 결합한다")
  void testSkillNewInOneSourceIsKept() {
    UUID userId = UUID.randomUUID();
    String ym = "202610";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

    Skill active =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill partial =
        Skill.builder().id(UUID.randomUUID()).standardName("Spring Boot").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(userSkillOf(active), userSkillOf(partial)));
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));
    stubWantedAndJumpitUnsuccessful();

    when(skillDemandCalculator.calculateDemandIndex(any(), eq(active.getId()), any(), eq(ym)))
        .thenReturn(demand(active.getId(), 120.0, SkillDemandStatus.OK));
    when(skillDemandCalculator.calculateDemandIndex(
            eq("JOBKOREA"), eq(partial.getId()), any(), eq(ym)))
        .thenReturn(demand(partial.getId(), 80.0, SkillDemandStatus.OK));
    // 이전 창을 과거 표본으로 채운 원천(잡코리아)과 채우지 않은 원천(사람인)이 갈린다.
    when(skillDemandCalculator.calculateDemandIndex(
            eq("SARAMIN"), eq(partial.getId()), any(), eq(ym)))
        .thenReturn(demand(partial.getId(), 0.0, SkillDemandStatus.NEW_OBSERVATION));

    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(
            Map.of(
                active.getId(), BigDecimal.valueOf(0.5),
                partial.getId(), BigDecimal.valueOf(0.5)));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, ym);

    // 표본 미달과 같은 결합: Java 는 두 원천 모두 120, Spring Boot 는 사람인이 빠져 잡코리아 80 만으로 → 0.5×120 + 0.5×80 = 100.
    assertThat(result.excludedSkills()).isEmpty();
    assertThat(result.value()).isEqualByComparingTo("100.0");
  }

  @Test
  @DisplayName("모든 원천에서 신규 관측인 스킬은 신규 관측으로 제외된다")
  void testSkillNewInAllSourcesIsExcluded() {
    UUID userId = UUID.randomUUID();
    String ym = "202610";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

    Skill active =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill fresh =
        Skill.builder().id(UUID.randomUUID()).standardName("Spring Boot").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(userSkillOf(active), userSkillOf(fresh)));
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));

    when(skillDemandCalculator.calculateDemandIndex(any(), eq(active.getId()), any(), eq(ym)))
        .thenReturn(demand(active.getId(), 120.0, SkillDemandStatus.OK));
    when(skillDemandCalculator.calculateDemandIndex(any(), eq(fresh.getId()), any(), eq(ym)))
        .thenReturn(demand(fresh.getId(), 0.0, SkillDemandStatus.NEW_OBSERVATION));

    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(
            Map.of(
                active.getId(), BigDecimal.valueOf(0.5),
                fresh.getId(), BigDecimal.valueOf(0.5)));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, ym);

    assertThat(result.excludedSkills())
        .extracting(ExcludedSkill::reason)
        .containsExactly(SkillExclusionReason.NEW_OBSERVATION);
    assertThat(result.value()).isEqualByComparingTo("120.0");
  }

  @Test
  @DisplayName("한 원천에서만 표본이 미달이면 제외하지 않고 그 스킬은 지수를 낸 원천끼리 결합한다")
  void testSkillUnderSampledInOneSourceIsKept() {
    UUID userId = UUID.randomUUID();
    String ym = "202607";

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

    Skill active =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    Skill partial =
        Skill.builder().id(UUID.randomUUID()).standardName("Kotlin").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId))
        .thenReturn(List.of(userSkillOf(active), userSkillOf(partial)));
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenReturn(resolvedGroup("JOBKOREA", 50));
    stubWantedAndJumpitUnsuccessful();

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

    // 스킬마다 지수를 낸 원천끼리 결합한다: Java = 두 원천 모두 120, Kotlin = 사람인이 빠져 잡코리아 80.
    // 0.5×120 + 0.5×80 = 100. 사람인의 빠진 Kotlin 을 0 으로 채우면 (0.5×120+0.5×80 + 0.5×120)/2 = 80 이 나온다.
    assertThat(result.excludedSkills()).isEmpty();
    assertThat(result.value()).isEqualByComparingTo("100.0");
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
    stubWantedAndJumpitUnsuccessful();

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
    // 원천 4개 모두 상세에 오른다 — 원티드·점핏은 동료집단이 비어 표본 0 으로 남는다
    assertThat(stdDetail.getSources()).hasSize(4);

    var custDetail =
        details.stream()
            .filter(d -> d.getSkillId().equals(customSkill.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(custDetail.getStatus()).isEqualTo("CUSTOM_SKILL_NO_STATS");
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

  /** 표본·정기 수집 두 원천만 보는 테스트가 원티드·점핏을 빈 원천으로 둔다. */
  private void stubWantedAndJumpitUnsuccessful() {
    for (String source : List.of("WANTED", "JUMPIT")) {
      when(peerGroupFallbackResolver.resolveForSource(eq(source), any(), any()))
          .thenReturn(unsuccessfulGroup());
      // 뒤에 오는 any() 수요 스텁이 덮어도 되므로 lenient
      lenient()
          .when(skillDemandCalculator.calculateDemandIndex(eq(source), any(), any(), any()))
          .thenReturn(demand(UUID.randomUUID(), 0.0, SkillDemandStatus.INSUFFICIENT_SAMPLE));
    }
  }

  private static PeerGroupFallbackResolver.ResolvedPeerGroup unsuccessfulGroup() {
    return PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
        .successful(false)
        .sampleCount(0)
        .fallbackLevel(0)
        .coverage(new SourceCoverage("NONE", 0, 0, false, "조건", BigDecimal.ZERO))
        .build();
  }

  @Test
  @DisplayName("원천 4개를 조회하고, 이전 창이 빈 점핏은 신규 관측으로 빠지며 커버리지에 남는다")
  void fourSourcesJumpitNewObservationExcluded() {
    UUID userId = UUID.randomUUID();
    String ym = "202609";
    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);
    Skill java = Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();

    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkillOf(java)));
    when(peerGroupFallbackResolver.resolveForSource(any(), any(), any()))
        .thenAnswer(inv -> resolvedGroup(inv.getArgument(0), 50));
    when(skillDemandCalculator.calculateDemandIndex(any(), eq(java.getId()), any(), eq(ym)))
        .thenReturn(demand(java.getId(), 120.0, SkillDemandStatus.OK));
    when(skillDemandCalculator.calculateDemandIndex(eq("JUMPIT"), eq(java.getId()), any(), eq(ym)))
        .thenReturn(demand(java.getId(), 0.0, SkillDemandStatus.NEW_OBSERVATION));
    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(java.getId(), BigDecimal.valueOf(1.0)));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202606"));

    MviResult result = service.calculate(userId, ym);

    assertThat(result.sources())
        .extracting(SourceCoverage::source)
        .containsExactly("JOBKOREA", "SARAMIN", "WANTED", "JUMPIT");
    assertThat(result.value()).isEqualByComparingTo("120.0");
    assertThat(result.excludedSkills()).isEmpty();
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
  @DisplayName("기준월은 잡코리아 표본 집계가 있는 최신 월이다 — 다른 원천 집계만 있는 당월로 잡지 않는다")
  void testFallbackToLatestAvailableYearMonth() {
    UUID userId = UUID.randomUUID();
    String requestedYm = "202609";
    String latestAvailableYm = "202608";

    // 당월은 사람인·통합 집계만 있고 잡코리아 표본은 아직 없다 — 원천 무관 최신 월(당월)은 조회하지 않는다.
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource("JOBKOREA", requestedYm))
        .thenReturn(Optional.of(latestAvailableYm));

    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);

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
    stubWantedAndJumpitUnsuccessful();

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

  @Test
  @DisplayName("정기 수집 원천의 동료집단이 표본 원천보다 커도 결합 가중치는 표본 원천 건수로 묶인다")
  void testCrawlSourceWeightCappedAtSampledSourceCount() {
    UUID userId = UUID.randomUUID();
    String ym = "202610";
    Skill skill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    stubCombine(userId, ym, skill, resolvedGroup("JOBKOREA", 60), 100.0, 200.0);

    MviResult result = service.calculate(userId, ym);

    // 상한 없이는 (100×60 + 200×2000)/2060 = 197.1. 사람인을 60 으로 묶으면 (100×60 + 200×60)/120 = 150.
    assertThat(result.value().doubleValue()).isEqualTo(150.0);
    assertThat(result.skillContributions().get(0).demandIndex().doubleValue()).isEqualTo(150.0);
    assertThat(service.getSkillDemandDetails(userId, ym).get(0).getDemandIndex()).isEqualTo(150.0);
  }

  @Test
  @DisplayName("표본 원천 동료집단이 실패하면 정기 수집 원천은 상한 없이 그 값으로 결합된다")
  void testNoCapWhenSampledSourceUnsuccessful() {
    UUID userId = UUID.randomUUID();
    String ym = "202610";
    Skill skill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").isCustom(false).build();
    PeerGroupFallbackResolver.ResolvedPeerGroup failed =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source("JOBKOREA")
            .successful(false)
            .sampleCount(3)
            .fallbackLevel(4)
            .coverage(new SourceCoverage("JOBKOREA", 4, 3, false, "조건", BigDecimal.ZERO))
            .build();
    stubCombine(userId, ym, skill, failed, 100.0, 200.0);

    MviResult result = service.calculate(userId, ym);

    assertThat(result.value().doubleValue()).isEqualTo(200.0);
  }

  private void stubCombine(
      UUID userId,
      String ym,
      Skill skill,
      PeerGroupFallbackResolver.ResolvedPeerGroup jobkorea,
      double jobkoreaIndex,
      double saraminIndex) {
    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);
    when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    when(userSkillRepository.findByUserId(userId)).thenReturn(List.of(userSkillOf(skill)));
    when(peerGroupFallbackResolver.resolveForSource(eq("JOBKOREA"), any(), any()))
        .thenReturn(jobkorea);
    when(peerGroupFallbackResolver.resolveForSource(eq("SARAMIN"), any(), any()))
        .thenReturn(resolvedGroup("SARAMIN", 2000));
    stubWantedAndJumpitUnsuccessful();
    when(skillDemandCalculator.calculateDemandIndex(
            eq("JOBKOREA"), eq(skill.getId()), any(), eq(ym)))
        .thenReturn(demand(skill.getId(), jobkoreaIndex, SkillDemandStatus.OK));
    when(skillDemandCalculator.calculateDemandIndex(
            eq("SARAMIN"), eq(skill.getId()), any(), eq(ym)))
        .thenReturn(demand(skill.getId(), saraminIndex, SkillDemandStatus.OK));
    when(skillWeightCalculator.calculateWeights(any(), any(), any()))
        .thenReturn(Map.of(skill.getId(), BigDecimal.ONE));
    when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202609"));
  }

  @Test
  @DisplayName("스킬에 지수를 내지 않는 원천(신규 관측)은 그 스킬의 정기 수집 상한 합계에 들지 않는다")
  void nonContributingSourceExcludedFromSkillCap() {
    UUID userId = UUID.randomUUID();
    String ym = "202610";
    Skill java = skill("Java");
    stubSources(
        userId,
        ym,
        Map.of("JOBKOREA", 250, "SARAMIN", 200, "JUMPIT", 554),
        Map.of(java, BigDecimal.ONE),
        Map.of(
            java,
            Map.of(
                "JOBKOREA", demand(java.getId(), 100.0, 0.10, 0.10, 100, 100, SkillDemandStatus.OK),
                "SARAMIN", demand(java.getId(), 140.0, 0.14, 0.10, 50, 50, SkillDemandStatus.OK),
                "JUMPIT",
                    demand(java.getId(), 0.0, 0.0, 0.0, 0, 0, SkillDemandStatus.NEW_OBSERVATION))));

    MviResult result = service.calculate(userId, ym);

    // 점핏은 지수를 내지 않아 상한 계산에서 빠진다. 사람인 200 ≤ 잡코리아 250 이라 줄이지 않는다:
    // (250×100 + 200×140)/(250+200) = 117.8. 점핏을 세면 사람인이 250×200/754 = 66.3 으로 줄어 108.4 가 된다.
    assertThat(result.value()).isEqualByComparingTo("117.8");
    assertThat(result.skillContributions().get(0).demandIndex()).isEqualByComparingTo("117.8");
    // 원천 비중 250/450·200/450 로 분산을 더한다: √((5/9)²·100²·0.02 + (4/9)²·140²·0.04) = 14.7
    assertThat(result.standardError()).isEqualByComparingTo("14.7");
    // 커버리지의 원천별 표본 수는 상한을 적용하지 않은 실제 수다.
    assertThat(result.sources())
        .filteredOn(c -> "JUMPIT".equals(c.source()))
        .extracting(SourceCoverage::sampleCount)
        .containsExactly(554);
  }

  @Test
  @DisplayName("스킬에 지수를 내는 정기 수집 원천 합계가 상한을 넘으면 비례로 줄고, 상한은 지수를 내는 표본 원천에서만 잡는다")
  void contributingRegularSourcesScaledToContributingSampledMax() {
    UUID userId = UUID.randomUUID();
    String ym = "202610";
    Skill java = skill("Java");
    stubSources(
        userId,
        ym,
        Map.of("JOBKOREA", 100, "WANTED", 400, "SARAMIN", 200, "JUMPIT", 300),
        Map.of(java, BigDecimal.ONE),
        Map.of(
            java,
            Map.of(
                "JOBKOREA", demand(java.getId(), 100.0, SkillDemandStatus.OK),
                "WANTED", demand(java.getId(), 0.0, SkillDemandStatus.NEW_OBSERVATION),
                "SARAMIN", demand(java.getId(), 140.0, SkillDemandStatus.OK),
                "JUMPIT", demand(java.getId(), 80.0, SkillDemandStatus.OK))));

    MviResult result = service.calculate(userId, ym);

    // 상한 = 잡코리아 100(원티드는 지수를 내지 않아 상한을 잡지 않는다). 정기 수집 합계 500 → 0.2 배: 사람인 40·점핏 60.
    // (100×100 + 40×140 + 60×80)/200 = 102.0. 원티드 400 을 상한으로 쓰면 103.2 가 된다.
    assertThat(result.value()).isEqualByComparingTo("102.0");
    assertThat(result.skillContributions().get(0).demandIndex()).isEqualByComparingTo("102.0");
    assertThat(service.getSkillDemandDetails(userId, ym).get(0).getDemandIndex()).isEqualTo(102.0);
  }

  @Test
  @DisplayName("스킬마다 지수를 낸 원천끼리 결합한 뒤 스킬 가중치로 합친다 — 기여도 합이 지수와 맞는다")
  void combinesPerSkillThenAcrossSkills() {
    UUID userId = UUID.randomUUID();
    String ym = "202610";
    Skill java = skill("Java");
    Skill kotlin = skill("Kotlin");
    stubSources(
        userId,
        ym,
        Map.of("JOBKOREA", 250, "SARAMIN", 200, "JUMPIT", 554),
        Map.of(java, new BigDecimal("0.5"), kotlin, new BigDecimal("0.5")),
        Map.of(
            java,
            Map.of(
                "JOBKOREA", demand(java.getId(), 100.0, SkillDemandStatus.OK),
                "SARAMIN", demand(java.getId(), 140.0, SkillDemandStatus.OK),
                "JUMPIT", demand(java.getId(), 0.0, SkillDemandStatus.NEW_OBSERVATION)),
            kotlin,
            Map.of(
                "JOBKOREA", demand(kotlin.getId(), 120.0, SkillDemandStatus.OK),
                "SARAMIN", demand(kotlin.getId(), 0.0, SkillDemandStatus.INSUFFICIENT_SAMPLE),
                "JUMPIT", demand(kotlin.getId(), 0.0, SkillDemandStatus.NEW_OBSERVATION))));

    MviResult result = service.calculate(userId, ym);

    // Java = (250×100 + 200×140)/450 = 117.78, Kotlin = 잡코리아만 120. 0.5×117.78 + 0.5×120 = 118.9.
    // 원천별 지수를 먼저 접으면 잡코리아 110·사람인 140 을 250:200 으로 합쳐 123.3 이 된다.
    assertThat(result.value()).isEqualByComparingTo("118.9");
    BigDecimal contributionSum =
        result.skillContributions().stream()
            .map(c -> c.contribution())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(contributionSum.doubleValue())
        .isCloseTo(118.9, org.assertj.core.data.Offset.offset(0.05));
  }

  @Test
  @DisplayName("스킬 상세 - 지수를 낸 원천끼리 그 스킬의 결합 가중치로 다시 정규화한다")
  void detailsRenormalizeOverContributingSources() {
    UUID userId = UUID.randomUUID();
    String ym = "202610";
    Skill java = skill("Java");
    stubSources(
        userId,
        ym,
        Map.of("JOBKOREA", 250, "SARAMIN", 200, "JUMPIT", 554),
        Map.of(java, BigDecimal.ONE),
        Map.of(
            java,
            Map.of(
                "JOBKOREA", demand(java.getId(), 100.0, 0.10, 0.10, 100, 100, SkillDemandStatus.OK),
                "SARAMIN", demand(java.getId(), 140.0, 0.14, 0.10, 50, 50, SkillDemandStatus.OK),
                "JUMPIT",
                    demand(java.getId(), 0.0, 0.0, 0.0, 0, 0, SkillDemandStatus.NEW_OBSERVATION))));

    var detail = service.getSkillDemandDetails(userId, ym).get(0);

    // 비중 250/450·200/450: 지수 117.8, 최근 수요율 (250×0.10 + 200×0.14)/450 = 0.1178, 이전 0.1000.
    assertThat(detail.getDemandIndex()).isEqualTo(117.8);
    assertThat(detail.getRecentDemandRate()).isEqualTo(0.1178);
    assertThat(detail.getBaselineDemandRate()).isEqualTo(0.1);
    assertThat(detail.getStatus()).isEqualTo("OK");
  }

  private static Skill skill(String name) {
    return Skill.builder().id(UUID.randomUUID()).standardName(name).isCustom(false).build();
  }

  private static SkillDemandCalculator.SkillDemandResult demand(
      UUID skillId,
      double index,
      double recentRate,
      double baselineRate,
      int recentSkillPostings,
      int baselineSkillPostings,
      SkillDemandStatus status) {
    return SkillDemandCalculator.SkillDemandResult.builder()
        .skillId(skillId)
        .demandIndex(index)
        .recentDemandRate(recentRate)
        .baselineDemandRate(baselineRate)
        .recentSkillPostings(recentSkillPostings)
        .baselineSkillPostings(baselineSkillPostings)
        .status(status)
        .build();
  }

  /** 원천별 동료집단 표본 수(없는 원천은 실패)와 스킬·원천별 수요 결과를 스텁한다. */
  private void stubSources(
      UUID userId,
      String ym,
      Map<String, Integer> sampleCounts,
      Map<Skill, BigDecimal> skillWeights,
      Map<Skill, Map<String, SkillDemandCalculator.SkillDemandResult>> demands) {
    CareerProfile profile = new CareerProfile(userId);
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM", null, null);
    lenient().when(careerProfileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
    List<UserSkill> userSkills =
        skillWeights.keySet().stream()
            .sorted(java.util.Comparator.comparing(Skill::getStandardName))
            .map(MarketValueIndexServiceTest::userSkillOf)
            .toList();
    lenient().when(userSkillRepository.findByUserId(userId)).thenReturn(userSkills);
    for (String source : MarketValueIndexServiceImpl.SUPPORTED_SOURCES) {
      Integer count = sampleCounts.get(source);
      lenient()
          .when(peerGroupFallbackResolver.resolveForSource(eq(source), any(), any()))
          .thenReturn(count == null ? unsuccessfulGroup() : resolvedGroup(source, count));
    }
    demands.forEach(
        (skill, bySource) ->
            bySource.forEach(
                (source, result) ->
                    lenient()
                        .when(
                            skillDemandCalculator.calculateDemandIndex(
                                eq(source), eq(skill.getId()), any(), eq(ym)))
                        .thenReturn(result)));
    Map<UUID, BigDecimal> weights = new java.util.HashMap<>();
    skillWeights.forEach((skill, w) -> weights.put(skill.getId(), w));
    lenient().when(skillWeightCalculator.calculateWeights(any(), any(), any())).thenReturn(weights);
    lenient()
        .when(wageBaselineResolver.resolveBaseline(any()))
        .thenReturn(new WageBaseline(65000000L, "초과급여 제외", "202609"));
  }

  private static UserSkill userSkillOf(Skill skill) {
    return UserSkill.builder()
        .skill(skill)
        .monthsOfExperience(24)
        .lastUsedDate(LocalDate.of(2026, 7, 1))
        .build();
  }
}
