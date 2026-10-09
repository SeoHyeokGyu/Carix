package com.carix.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import com.carix.pipeline.dto.CollectionPriorityItem;
import com.carix.pipeline.dto.CollectionPriorityItem.PriorityGrade;
import com.carix.pipeline.dto.CollectionPriorityResponse;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MviCollectionPriorityServiceTest {

  @Mock private MarketAggregateRepository marketAggregateRepository;

  @InjectMocks private MviCollectionPriorityService service;

  @Test
  @DisplayName("집계 데이터가 비어 있을 때 표준 차원 조합이 모두 CRITICAL 우선순위로 도출된다")
  void calculatePriorities_emptyMarketData_allCritical() {
    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202607", "ALL"))
        .thenReturn(List.of());

    CollectionPriorityResponse response = service.calculatePriorities("202607", "ALL", 10, false);

    assertThat(response.baseYearMonth()).isEqualTo("202607");
    assertThat(response.underSampledCount()).isGreaterThan(0);
    assertThat(response.priorities()).isNotEmpty();

    CollectionPriorityItem firstItem = response.priorities().get(0);
    assertThat(firstItem.priorityGrade()).isEqualTo(PriorityGrade.CRITICAL);
    assertThat(firstItem.currentSampleCount()).isEqualTo(0);
    assertThat(firstItem.sampleDeficit()).isEqualTo(10);
    assertThat(firstItem.priorityReason()).contains("완전 결측 상태");
  }

  @Test
  @DisplayName("표본수가 10 이상인 항목은 SUFFICIENT 등급이 부여되고, underSampledOnly=true 시 제외된다")
  void calculatePriorities_sufficientData_filteredWhenUnderSampledOnly() {
    MarketAggregate sufficientAgg =
        MarketAggregate.builder()
            .jobFamily("DEVELOPER")
            .region("ALL")
            .experienceBracket("MID")
            .postingCount(15)
            .build();

    MarketAggregate sparseAgg =
        MarketAggregate.builder()
            .jobFamily("DATA")
            .region("ALL")
            .experienceBracket("ENTRY")
            .postingCount(3)
            .build();

    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202607", "ALL"))
        .thenReturn(List.of(sufficientAgg, sparseAgg));

    // underSampledOnly = false
    CollectionPriorityResponse fullResponse =
        service.calculatePriorities("202607", "ALL", 10, false);

    boolean hasSufficient =
        fullResponse.priorities().stream()
            .anyMatch(
                i ->
                    "DEVELOPER".equals(i.jobFamily())
                        && "MID".equals(i.experienceBracket())
                        && i.priorityGrade() == PriorityGrade.SUFFICIENT);
    assertThat(hasSufficient).isTrue();

    // underSampledOnly = true
    CollectionPriorityResponse filteredResponse =
        service.calculatePriorities("202607", "ALL", 10, true);

    boolean filteredHasSufficient =
        filteredResponse.priorities().stream()
            .anyMatch(i -> i.priorityGrade() == PriorityGrade.SUFFICIENT);
    assertThat(filteredHasSufficient).isFalse();
  }

  @Test
  @DisplayName("우선순위 정렬은 CRITICAL > HIGH > MEDIUM 순으로 결손수가 큰 항목이 상위에 배치된다")
  void calculatePriorities_sortedByPriorityAndDeficit() {
    MarketAggregate criticalAgg =
        MarketAggregate.builder()
            .jobFamily("AI")
            .region("ALL")
            .experienceBracket("LEAD")
            .postingCount(0)
            .build();

    MarketAggregate highAgg =
        MarketAggregate.builder()
            .jobFamily("DEVELOPER")
            .region("ALL")
            .experienceBracket("ENTRY")
            .postingCount(2) // deficit 8
            .build();

    MarketAggregate mediumAgg =
        MarketAggregate.builder()
            .jobFamily("DATA")
            .region("ALL")
            .experienceBracket("MID")
            .postingCount(8) // deficit 2
            .build();

    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202607", "ALL"))
        .thenReturn(List.of(criticalAgg, highAgg, mediumAgg));

    CollectionPriorityResponse response = service.calculatePriorities("202607", "ALL", 10, true);

    List<CollectionPriorityItem> items = response.priorities();
    assertThat(items).isNotEmpty();

    // 상위 항목들의 등급이 CRITICAL -> HIGH -> MEDIUM 순서인지 검증
    PriorityGrade prevGrade = PriorityGrade.CRITICAL;
    for (CollectionPriorityItem item : items) {
      assertThat(item.priorityGrade().compareTo(prevGrade)).isGreaterThanOrEqualTo(0);
      prevGrade = item.priorityGrade();
    }
  }

  @Test
  @DisplayName("원천 플랫폼 지정 시 원천별 집계 쿼리를 조회한다")
  void calculatePriorities_specificSource() {
    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202607", "SARAMIN"))
        .thenReturn(List.of());

    CollectionPriorityResponse response =
        service.calculatePriorities("202607", "SARAMIN", 10, false);

    assertThat(response.source()).isEqualTo("SARAMIN");
  }

  @Test
  @DisplayName("기준월 미지정 시 당월(UTC) 이하에서 ALL 행이 있는 최신 월을 쓴다 — 프레임 행만 있는 달은 건너뛴다")
  void calculatePriorities_nullMonth_usesLatestMonthWithAllRow() {
    String currentMonth =
        YearMonth.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMM"));
    when(marketAggregateRepository.findLatestYearMonthForSource("ALL", currentMonth))
        .thenReturn(Optional.of("202609"));
    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202609", "ALL"))
        .thenReturn(List.of());

    CollectionPriorityResponse response = service.calculatePriorities(null, null, null, false);

    assertThat(response.baseYearMonth()).isEqualTo("202609");
    assertThat(response.minSampleSize()).isEqualTo(10);
    verify(marketAggregateRepository, never()).findDistinctBaseYearMonths();
  }

  @Test
  @DisplayName("기준월 미지정이고 ALL 행이 없으면 당월(UTC)을 쓴다")
  void calculatePriorities_nullMonth_fallsBackToCurrentMonth() {
    String currentMonth =
        YearMonth.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMM"));
    when(marketAggregateRepository.findLatestYearMonthForSource("ALL", currentMonth))
        .thenReturn(Optional.empty());
    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource(currentMonth, "ALL"))
        .thenReturn(List.of());

    CollectionPriorityResponse response = service.calculatePriorities(null, null, null, false);

    assertThat(response.baseYearMonth()).isEqualTo(currentMonth);
  }

  @Test
  @DisplayName("기본 ALL 모드는 source=ALL 행만 읽고 원천별 행을 합산하지 않는다")
  void calculatePriorities_defaultAll_readsOnlyAllSourceRows() {
    MarketAggregate allRow =
        MarketAggregate.builder()
            .jobFamily("DEVELOPER")
            .region("ALL")
            .experienceBracket("MID")
            .postingCount(7)
            .build();
    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202607", "ALL"))
        .thenReturn(List.of(allRow));

    CollectionPriorityResponse response = service.calculatePriorities("202607", "ALL", 10, false);

    assertThat(response.priorities())
        .filteredOn(
            i ->
                "DEVELOPER".equals(i.jobFamily())
                    && "MID".equals(i.experienceBracket())
                    && "ALL".equals(i.region()))
        .singleElement()
        .satisfies(i -> assertThat(i.currentSampleCount()).isEqualTo(7));
  }
}
