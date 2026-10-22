package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carix.config.IndexThresholdProperties;
import com.carix.index.dto.CollectionPriorityItem;
import com.carix.index.dto.CollectionPriorityItem.PriorityGrade;
import com.carix.index.dto.CollectionPriorityResponse;
import com.carix.market.MarketAggregate;
import com.carix.market.MarketAggregateRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MviCollectionPriorityServiceTest {

  @Mock private MarketAggregateRepository marketAggregateRepository;

  @InjectMocks private MviCollectionPriorityService service;

  private IndexThresholdProperties thresholds;

  @BeforeEach
  void setUp() {
    thresholds = new IndexThresholdProperties();
    thresholds.setPeerGroupMinSampleSize(10);
    ReflectionTestUtils.setField(service, "thresholds", thresholds);
  }

  @Test
  @DisplayName("외부화된 임계값(minSampleSize=15) 설정 시 해당 기준에 맞춰 결손수와 등급이 산정된다")
  void calculatePriorities_customThreshold() {
    thresholds.setPeerGroupMinSampleSize(15);

    MarketAggregate agg =
        MarketAggregate.builder()
            .jobFamily("DEVELOPER")
            .region("ALL")
            .experienceBracket("MID")
            .postingCount(12) // deficit 3 (under 15)
            .build();

    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202607", "ALL"))
        .thenReturn(List.of(agg));

    CollectionPriorityResponse response = service.calculatePriorities("202607", "ALL", null, false);

    CollectionPriorityItem devMid =
        response.priorities().stream()
            .filter(i -> "DEVELOPER".equals(i.jobFamily()) && "MID".equals(i.experienceBracket()))
            .findFirst()
            .orElseThrow();

    assertThat(devMid.requiredSampleSize()).isEqualTo(15);
    assertThat(devMid.currentSampleCount()).isEqualTo(12);
    assertThat(devMid.sampleDeficit()).isEqualTo(3);
    assertThat(devMid.priorityGrade()).isEqualTo(PriorityGrade.MEDIUM);
    assertThat(devMid.priorityReason()).contains("최소 기준(15건)에 미달");
  }

  @Test
  @DisplayName("CRITICAL, HIGH, MEDIUM, SUFFICIENT 등급 판정 및 정렬 순서 검증")
  void calculatePriorities_allGradesAndSorting() {
    MarketAggregate criticalAgg =
        MarketAggregate.builder()
            .jobFamily("DATA")
            .region("ALL")
            .experienceBracket("ENTRY")
            .postingCount(0)
            .build();
    MarketAggregate highAgg =
        MarketAggregate.builder()
            .jobFamily("DEVELOPER")
            .region("ALL")
            .experienceBracket("JUNIOR")
            .postingCount(3) // 3 < (10+1)/2=5 -> HIGH
            .build();
    MarketAggregate mediumAgg =
        MarketAggregate.builder()
            .jobFamily("DEVELOPER")
            .region("ALL")
            .experienceBracket("MID")
            .postingCount(7) // 7 < 10 -> MEDIUM
            .build();
    MarketAggregate sufficientAgg =
        MarketAggregate.builder()
            .jobFamily("DEVELOPER")
            .region("ALL")
            .experienceBracket("SENIOR")
            .postingCount(25) // 25 >= 10 -> SUFFICIENT
            .build();

    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202608", "ALL"))
        .thenReturn(List.of(criticalAgg, highAgg, mediumAgg, sufficientAgg));

    CollectionPriorityResponse response = service.calculatePriorities("2026-08", "ALL", 10, false);

    assertThat(response.baseYearMonth()).isEqualTo("202608");
    assertThat(response.totalEvaluatedCombinations()).isGreaterThanOrEqualTo(4);

    // 정렬 순서 확인: deficit 큰 순서대로
    List<CollectionPriorityItem> items = response.priorities();
    assertThat(items.get(0).sampleDeficit()).isGreaterThanOrEqualTo(items.get(1).sampleDeficit());

    CollectionPriorityItem highItem =
        items.stream()
            .filter(
                i -> "JUNIOR".equals(i.experienceBracket()) && "DEVELOPER".equals(i.jobFamily()))
            .findFirst()
            .orElseThrow();
    assertThat(highItem.priorityGrade()).isEqualTo(PriorityGrade.HIGH);
    assertThat(highItem.priorityReason()).contains("극심한 결손");

    CollectionPriorityItem suffItem =
        items.stream()
            .filter(
                i -> "SENIOR".equals(i.experienceBracket()) && "DEVELOPER".equals(i.jobFamily()))
            .findFirst()
            .orElseThrow();
    assertThat(suffItem.priorityGrade()).isEqualTo(PriorityGrade.SUFFICIENT);
    assertThat(suffItem.sampleDeficit()).isEqualTo(0);
    assertThat(suffItem.priorityReason()).contains("최소 기준(10건)을 충족");
  }

  @Test
  @DisplayName("underSampledOnly=true 지정 시 SUFFICIENT 등급 항목은 결과에서 제외된다")
  void calculatePriorities_underSampledOnly() {
    MarketAggregate sufficientAgg =
        MarketAggregate.builder()
            .jobFamily("DEVELOPER")
            .region("ALL")
            .experienceBracket("SENIOR")
            .postingCount(30)
            .build();
    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202607", "ALL"))
        .thenReturn(List.of(sufficientAgg));

    CollectionPriorityResponse response = service.calculatePriorities("202607", "ALL", 10, true);

    assertThat(response.priorities())
        .noneMatch(item -> item.priorityGrade() == PriorityGrade.SUFFICIENT);
  }

  @Test
  @DisplayName("source 를 안 주면 source='ALL' 행만 읽고 원천별 행은 합산하지 않는다")
  void calculatePriorities_defaultReadsOnlyAllRows() {
    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202607", "ALL"))
        .thenReturn(List.of());

    CollectionPriorityResponse response = service.calculatePriorities("202607", null, 10, false);

    assertThat(response.source()).isEqualTo("ALL");
    verify(marketAggregateRepository).findPeerGroupAggregatesByMonthAndSource("202607", "ALL");
    verify(marketAggregateRepository, never())
        .findPeerGroupAggregatesByMonthAndSource("202607", "JOBKOREA");
  }

  @Test
  @DisplayName("기준월 미지정 시 ALL 행이 있는 최신 월을 대상으로 조회한다 - 프레임 행만 있는 달은 건너뛴다")
  void calculatePriorities_nullMonth_usesLatestMonthWithAllRow() {
    when(marketAggregateRepository.findLatestAvailableYearMonthForSource(eq("ALL"), any()))
        .thenReturn(Optional.of("202610"));
    when(marketAggregateRepository.findPeerGroupAggregatesByMonthAndSource("202610", "ALL"))
        .thenReturn(List.of());

    CollectionPriorityResponse response = service.calculatePriorities(null, null, null, false);

    assertThat(response.baseYearMonth()).isEqualTo("202610");
    verify(marketAggregateRepository, never()).findDistinctBaseYearMonths();
  }
}
