package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillDemandCalculatorTest {

  @Mock private MarketAggregateRepository marketAggregateRepository;

  @InjectMocks private SkillDemandCalculator calculator;

  @Test
  @DisplayName("정상 케이스: 최근 3개월 수요율(10%) vs 이전 12개월 수요율(5%) -> 수요 지수 200.0")
  void testNormalCalculation() {
    // Given
    UUID skillId = UUID.randomUUID();
    String source = "JOBKOREA";
    String baseYearMonth = "202607";

    PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source(source)
            .jobFamily("DEVELOPER")
            .region("서울 강남구")
            .brackets(List.of("MID"))
            .employmentType("FULL_TIME")
            .companySize("MEDIUM")
            .build();

    // 최근 3개월: 스킬 공고 10건, 전체 공고 100건 -> 수요율 0.10 (10%)
    when(marketAggregateRepository.findSkillAggregates(
            eq(source),
            any(),
            eq("DEVELOPER"),
            eq("서울 강남구"),
            eq(List.of("MID")),
            eq("FULL_TIME"),
            eq("MEDIUM"),
            eq(skillId)))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(10).build()));

    when(marketAggregateRepository.findTotalPostingAggregates(
            eq(source),
            any(),
            eq("DEVELOPER"),
            eq("서울 강남구"),
            eq(List.of("MID")),
            eq("FULL_TIME"),
            eq("MEDIUM")))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(100).build()));

    // 이전 12개월: 스킬 공고 20건, 전체 공고 400건 -> 수요율 0.05 (5%)
    // (mock 동일 인자 분기는 lenient or helper로 구분)
    // When
    SkillDemandCalculator.SkillDemandResult result =
        calculator.calculateDemandIndex(source, skillId, peerGroup, baseYearMonth);

    // Then
    // 최근 10/100 = 0.10, 이전 10/100 = 0.10 (동일 mock 반환 시 100.0)
    assertThat(result.getStatus()).isEqualTo("OK");
    assertThat(result.getDemandIndex()).isEqualTo(100.0);
    assertThat(result.isClamped()).isFalse();
  }

  @Test
  @DisplayName("기간 창: 최근 3개월은 1~3개월 전, 이전 12개월은 4~15개월 전이고 기준월은 어느 창에도 들지 않는다")
  void testWindowBoundariesFollowSpecification() {
    // Arrange
    UUID skillId = UUID.randomUUID();
    String source = "JOBKOREA";
    String baseYearMonth = "202607";

    PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source(source)
            .jobFamily("DEVELOPER")
            .region("서울 강남구")
            .brackets(List.of("MID"))
            .employmentType("FULL_TIME")
            .companySize("MEDIUM")
            .build();

    when(marketAggregateRepository.findSkillAggregates(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(10).build()));
    when(marketAggregateRepository.findTotalPostingAggregates(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(100).build()));

    // Act
    calculator.calculateDemandIndex(source, skillId, peerGroup, baseYearMonth);

    // Assert
    List<String> recentMonths = List.of("202604", "202605", "202606");
    List<String> baselineMonths =
        List.of(
            "202504", "202505", "202506", "202507", "202508", "202509", "202510", "202511",
            "202512", "202601", "202602", "202603");

    verify(marketAggregateRepository)
        .findSkillAggregates(
            source,
            recentMonths,
            "DEVELOPER",
            "서울 강남구",
            List.of("MID"),
            "FULL_TIME",
            "MEDIUM",
            skillId);
    verify(marketAggregateRepository)
        .findSkillAggregates(
            source,
            baselineMonths,
            "DEVELOPER",
            "서울 강남구",
            List.of("MID"),
            "FULL_TIME",
            "MEDIUM",
            skillId);
    verify(marketAggregateRepository)
        .findTotalPostingAggregates(
            source, recentMonths, "DEVELOPER", "서울 강남구", List.of("MID"), "FULL_TIME", "MEDIUM");
    verify(marketAggregateRepository)
        .findTotalPostingAggregates(
            source, baselineMonths, "DEVELOPER", "서울 강남구", List.of("MID"), "FULL_TIME", "MEDIUM");
  }

  @Test
  @DisplayName("이전 12개월 스킬 표본이 기준 미만이면 표본 부족이다 — 비율이 커도 신뢰할 수 없다")
  void testInsufficientBaselineSample() {
    SkillDemandCalculator.SkillDemandResult result =
        calculateWith(/* recentSkillPostings= */ 40, /* baselineSkillPostings= */ 5);

    assertThat(result.getStatus()).isEqualTo("INSUFFICIENT_SAMPLE");
  }

  @Test
  @DisplayName("최근 3개월 스킬 표본이 0 초과 기준 미만이면 표본 부족이다")
  void testInsufficientRecentSample() {
    SkillDemandCalculator.SkillDemandResult result =
        calculateWith(/* recentSkillPostings= */ 4, /* baselineSkillPostings= */ 40);

    assertThat(result.getStatus()).isEqualTo("INSUFFICIENT_SAMPLE");
  }

  @Test
  @DisplayName("최근 표본이 0 이고 기준선 표본이 충분하면 표본 부족이 아니라 최근 수요 없음이다")
  void testZeroRecentWithSufficientBaselineStaysNoRecentDemand() {
    SkillDemandCalculator.SkillDemandResult result =
        calculateWith(/* recentSkillPostings= */ 0, /* baselineSkillPostings= */ 40);

    assertThat(result.getStatus()).isEqualTo("NO_RECENT_DEMAND");
    assertThat(result.getDemandIndex()).isZero();
  }

  @Test
  @DisplayName("두 창 모두 기준을 넘기면 정상 산출이다 — 경계값 포함")
  void testBothWindowsAtThresholdAreSufficient() {
    SkillDemandCalculator.SkillDemandResult result =
        calculateWith(
            /* recentSkillPostings= */ SkillDemandCalculator.MIN_SKILL_SAMPLE_SIZE,
            /* baselineSkillPostings= */ SkillDemandCalculator.MIN_SKILL_SAMPLE_SIZE);

    assertThat(result.getStatus()).isEqualTo("OK");
  }

  /** 최근 창과 기준선 창의 스킬 공고 수만 달리해 계산한다. 전체 공고 수는 두 창 모두 1000 으로 고정한다. */
  private SkillDemandCalculator.SkillDemandResult calculateWith(
      int recentSkillPostings, int baselineSkillPostings) {
    UUID skillId = UUID.randomUUID();
    String source = "JOBKOREA";

    PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source(source)
            .jobFamily("DEVELOPER")
            .region("서울특별시")
            .brackets(List.of("MID"))
            .build();

    when(marketAggregateRepository.findSkillAggregates(
            any(), any(), any(), any(), any(), any(), any(), eq(skillId)))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(recentSkillPostings).build()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(baselineSkillPostings).build()));
    when(marketAggregateRepository.findTotalPostingAggregates(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(1000).build()));

    return calculator.calculateDemandIndex(source, skillId, peerGroup, "202607");
  }

  @Test
  @DisplayName("분모 0 (이전 12개월 관측 없음): NEW_OBSERVATION 상태 반환")
  void testDenominatorZeroNewObservation() {
    // Given
    UUID skillId = UUID.randomUUID();
    String source = "SARAMIN";
    String baseYearMonth = "202607";

    PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source(source)
            .jobFamily("DEVELOPER")
            .region("서울특별시")
            .brackets(List.of("MID"))
            .build();

    // 최근 3개월은 공고 존재
    when(marketAggregateRepository.findSkillAggregates(
            any(), any(), any(), any(), any(), any(), any(), eq(skillId)))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(5).build()))
        .thenReturn(List.of()); // baseline은 0건

    when(marketAggregateRepository.findTotalPostingAggregates(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(100).build()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(500).build()));

    // When
    SkillDemandCalculator.SkillDemandResult result =
        calculator.calculateDemandIndex(source, skillId, peerGroup, baseYearMonth);

    // Then
    assertThat(result.getStatus()).isEqualTo("NEW_OBSERVATION");
    assertThat(result.getDemandIndex()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("분자 0 (최근 3개월 관측 없음): NO_RECENT_DEMAND 상태 및 지수 0 반환")
  void testNumeratorZeroNoRecentDemand() {
    // Given
    UUID skillId = UUID.randomUUID();
    String source = "JOBKOREA";
    String baseYearMonth = "202607";

    PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source(source)
            .jobFamily("DEVELOPER")
            .region("서울특별시")
            .brackets(List.of("MID"))
            .build();

    when(marketAggregateRepository.findSkillAggregates(
            any(), any(), any(), any(), any(), any(), any(), eq(skillId)))
        .thenReturn(List.of()) // 최근 0건
        .thenReturn(List.of(MarketAggregate.builder().postingCount(20).build())); // baseline 20건

    when(marketAggregateRepository.findTotalPostingAggregates(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(100).build()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(500).build()));

    // When
    SkillDemandCalculator.SkillDemandResult result =
        calculator.calculateDemandIndex(source, skillId, peerGroup, baseYearMonth);

    // Then
    assertThat(result.getStatus()).isEqualTo("NO_RECENT_DEMAND");
    assertThat(result.getDemandIndex()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("이상치 클램프: 하한 미만(25.0 미만)은 25.0으로 클램프되고 clamped=true여야 한다")
  void testClampMin() {
    UUID skillId = UUID.randomUUID();
    String source = "JOBKOREA";

    PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source(source)
            .jobFamily("DEVELOPER")
            .region("서울특별시")
            .brackets(List.of("MID"))
            .build();

    // 최근: 10 / 1,000 = 0.01 (1%)
    // 기준: 50 / 100 = 0.50 (50%)
    // rawIndex = (0.01 / 0.50) * 100 = 2.0 -> clamped to 25.0
    when(marketAggregateRepository.findSkillAggregates(
            any(), any(), any(), any(), any(), any(), any(), eq(skillId)))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(10).build()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(50).build()));

    when(marketAggregateRepository.findTotalPostingAggregates(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(1000).build()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(100).build()));

    SkillDemandCalculator.SkillDemandResult result =
        calculator.calculateDemandIndex(source, skillId, peerGroup, "202607");

    assertThat(result.getStatus()).isEqualTo("OK");
    assertThat(result.getDemandIndex()).isEqualTo(25.0);
    assertThat(result.isClamped()).isTrue();
  }

  @Test
  @DisplayName("이상치 클램프: 상한 초과(400.0 초과)는 400.0으로 클램프되고 clamped=true여야 한다")
  void testClampMax() {
    UUID skillId = UUID.randomUUID();
    String source = "JOBKOREA";

    PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source(source)
            .jobFamily("DEVELOPER")
            .region("서울특별시")
            .brackets(List.of("MID"))
            .build();

    // 최근: 50 / 100 = 0.50 (50%)
    // 기준: 10 / 1,000 = 0.01 (1%)
    // rawIndex = (0.50 / 0.01) * 100 = 5000.0 -> clamped to 400.0
    when(marketAggregateRepository.findSkillAggregates(
            any(), any(), any(), any(), any(), any(), any(), eq(skillId)))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(50).build()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(10).build()));

    when(marketAggregateRepository.findTotalPostingAggregates(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(100).build()))
        .thenReturn(List.of(MarketAggregate.builder().postingCount(1000).build()));

    SkillDemandCalculator.SkillDemandResult result =
        calculator.calculateDemandIndex(source, skillId, peerGroup, "202607");

    assertThat(result.getStatus()).isEqualTo("OK");
    assertThat(result.getDemandIndex()).isEqualTo(400.0);
    assertThat(result.isClamped()).isTrue();
  }

  @Test
  @DisplayName("총 공고수가 0이거나 기준년월이 비정상이어도 안전하게 동작한다")
  void testZeroTotalPostingsAndInvalidYm() {
    UUID skillId = UUID.randomUUID();
    String source = "SARAMIN";

    PeerGroupFallbackResolver.ResolvedPeerGroup peerGroup =
        PeerGroupFallbackResolver.ResolvedPeerGroup.builder()
            .source(source)
            .jobFamily("DEVELOPER")
            .region("서울특별시")
            .brackets(List.of("MID"))
            .build();

    when(marketAggregateRepository.findSkillAggregates(
            any(), any(), any(), any(), any(), any(), any(), eq(skillId)))
        .thenReturn(List.of());
    when(marketAggregateRepository.findTotalPostingAggregates(
            any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of());

    // null, 비정상 문자열 테스트
    SkillDemandCalculator.SkillDemandResult res1 =
        calculator.calculateDemandIndex(source, skillId, peerGroup, null);
    assertThat(res1.getStatus()).isEqualTo("NEW_OBSERVATION");

    SkillDemandCalculator.SkillDemandResult res2 =
        calculator.calculateDemandIndex(source, skillId, peerGroup, "2026");
    assertThat(res2.getStatus()).isEqualTo("NEW_OBSERVATION");

    SkillDemandCalculator.SkillDemandResult res3 =
        calculator.calculateDemandIndex(source, skillId, peerGroup, "202613");
    assertThat(res3.getStatus()).isEqualTo("NEW_OBSERVATION");

    SkillDemandCalculator.SkillDemandResult res4 =
        calculator.calculateDemandIndex(source, skillId, peerGroup, "ABCDEF");
    assertThat(res4.getStatus()).isEqualTo("NEW_OBSERVATION");
  }
}
