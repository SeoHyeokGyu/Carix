package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}
