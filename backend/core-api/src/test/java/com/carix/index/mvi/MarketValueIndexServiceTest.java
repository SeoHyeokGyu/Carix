package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.carix.index.dto.MviResult;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.WageBaseline;
import com.carix.index.enums.IndexStatus;
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
            .status("OK")
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
                .status("OK")
                .build());
    when(skillDemandCalculator.calculateDemandIndex(
            any(), eq(stale.getId()), any(), eq(baseYearMonth)))
        .thenReturn(
            SkillDemandCalculator.SkillDemandResult.builder()
                .skillId(stale.getId())
                .demandIndex(0.0)
                .status("NO_RECENT_DEMAND")
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
        .thenReturn(demand(active.getId(), 120.0, "OK"));
    when(skillDemandCalculator.calculateDemandIndex(any(), eq(scarce.getId()), any(), eq(ym)))
        .thenReturn(demand(scarce.getId(), 0.0, "INSUFFICIENT_SAMPLE"));

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
        .thenReturn(demand(active.getId(), 120.0, "OK"));
    when(skillDemandCalculator.calculateDemandIndex(
            eq("JOBKOREA"), eq(partial.getId()), any(), eq(ym)))
        .thenReturn(demand(partial.getId(), 80.0, "OK"));
    when(skillDemandCalculator.calculateDemandIndex(
            eq("SARAMIN"), eq(partial.getId()), any(), eq(ym)))
        .thenReturn(demand(partial.getId(), 0.0, "INSUFFICIENT_SAMPLE"));

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
      UUID skillId, double index, String status) {
    return SkillDemandCalculator.SkillDemandResult.builder()
        .skillId(skillId)
        .demandIndex(index)
        .status(status)
        .build();
  }

  private static UserSkill userSkillOf(Skill skill) {
    return UserSkill.builder()
        .skill(skill)
        .monthsOfExperience(24)
        .lastUsedDate(LocalDate.of(2026, 7, 1))
        .build();
  }
}
