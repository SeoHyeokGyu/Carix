package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.SnapshotHistoryResponse;
import com.carix.dashboard.dto.TrendListResponse;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.DecisionSignalCode;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스냅샷을 읽는 세 화면 API 검증 — `/dashboard/summary`, `/history`, `/trends`.
 *
 * <p>셋 다 {@code index_snapshot} 이 원천이고, 행이 없으면 <b>임의 값 대신 빈 상태</b>를 돌려준다. 스냅샷 재계산 트리거가 아직 배선되지
 * 않아(개발 계획 §5-N) 당분간 행이 없는 사용자가 정상이며, 그때 0 이나 100 을 채우면 §5-G 가 철거한 스텁과 같은 죄가 된다.
 */
class SnapshotReadServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private IndexSnapshotRepository repository;
  private SnapshotQueryService queryService;
  private DashboardSummaryService summaryService;
  private SnapshotSeriesService seriesService;

  @BeforeEach
  void setUp() {
    repository = mock(IndexSnapshotRepository.class);
    queryService = new SnapshotQueryService(repository);
    summaryService = new DashboardSummaryService(queryService);
    seriesService = new SnapshotSeriesService(queryService);
  }

  // --- /dashboard/summary ---

  @Test
  @DisplayName("summary - 스냅샷이 없으면 NOT_AVAILABLE 이고 지수는 null 이다")
  void summary_스냅샷없음() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of());

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.purchasingPowerIndex().value()).isNull();
    assertThat(response.purchasingPowerIndex().indexStatus())
        .isEqualTo(IndexStatus.NOT_AVAILABLE.name());
    assertThat(response.marketValueIndex().value()).isNull();
    assertThat(response.decisionSignal().code())
        .isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA.name());
  }

  @Test
  @DisplayName("summary - 스냅샷이 있으면 지수와 결정 신호를 그대로 싣는다")
  void summary_스냅샷있음() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.purchasingPowerIndex().value()).isEqualTo(94.2);
    assertThat(response.purchasingPowerIndex().indexStatus()).isEqualTo(IndexStatus.OK.name());
    assertThat(response.marketValueIndex().value()).isEqualTo(117.5);
    assertThat(response.decisionSignal().code()).isEqualTo("SEEK_JOB");
    assertThat(response.decisionSignal().label()).isEqualTo("이직 탐색");
  }

  @Test
  @DisplayName("summary - 전월 스냅샷이 있으면 지수 포인트 차이를 낸다")
  void summary_전월대비() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB),
                snapshot("202606", "95.40", "113.70", DecisionSignalCode.STAY)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.purchasingPowerIndex().changeFromLastMonth()).isEqualTo(-1.2);
    assertThat(response.purchasingPowerIndex().status()).isEqualTo("DECREASED");
    assertThat(response.marketValueIndex().changeFromLastMonth()).isEqualTo(3.8);
    assertThat(response.marketValueIndex().status()).isEqualTo("INCREASED");
  }

  @Test
  @DisplayName("summary - 전월이 없으면 변화량을 지어내지 않고 null 로 둔다")
  void summary_전월없음() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.purchasingPowerIndex().changeFromLastMonth()).isNull();
    assertThat(response.purchasingPowerIndex().status()).isNull();
  }

  @Test
  @DisplayName("summary - 원천별 커버리지는 rationale 에 없어 metadata 를 채우지 않는다 (§5-M)")
  void summary_커버리지_미저장() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB)));

    DashboardSummaryResponse.SummaryMetadata metadata =
        summaryService.resolve(USER_ID, "202607").metadata();

    assertThat(metadata.dataVersion()).isEqualTo("20260701093000-000");
    assertThat(metadata.ruleVersion()).isEqualTo("1.2.0");
    assertThat(metadata.baseDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    // 아래 넷은 MviResult 에만 있고 저장되지 않는다. 임의 값으로 채우지 않는다.
    assertThat(metadata.sampleSize()).isNull();
    assertThat(metadata.salaryMissingRate()).isNull();
    assertThat(metadata.peerGroup()).isNull();
    assertThat(metadata.sources()).isEmpty();
  }

  // --- /dashboard/history ---

  @Test
  @DisplayName("history - 스냅샷이 없으면 빈 배열이다")
  void history_비어있음() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of());

    SnapshotHistoryResponse response = seriesService.history(USER_ID);

    assertThat(response.snapshots()).isEmpty();
  }

  @Test
  @DisplayName("history - 같은 월에 여러 행이 있으면 data_version·rule_version 이 가장 최신인 하나만 쓴다")
  void history_월별_최신선택() {
    IndexSnapshot older = snapshotWithVersions("202607", "90.00", "1.9.0", "20260701093000-000");
    IndexSnapshot newer = snapshotWithVersions("202607", "94.20", "1.10.0", "20260701093000-000");
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(older, newer));

    SnapshotHistoryResponse response = seriesService.history(USER_ID);

    assertThat(response.snapshots()).hasSize(1);
    assertThat(response.snapshots().get(0).purchasingPowerIndex()).isEqualTo(94.2);
    assertThat(response.snapshots().get(0).ruleVersion()).isEqualTo("1.10.0");
  }

  @Test
  @DisplayName("history - 계산 불가였던 달은 지수가 null 로 남는다")
  void history_계산불가월() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202606", null, null, DecisionSignalCode.INSUFFICIENT_DATA)));

    SnapshotHistoryResponse response = seriesService.history(USER_ID);

    assertThat(response.snapshots().get(0).purchasingPowerIndex()).isNull();
    assertThat(response.snapshots().get(0).marketValueIndex()).isNull();
    assertThat(response.snapshots().get(0).decisionSignal()).isEqualTo("INSUFFICIENT_DATA");
  }

  // --- /dashboard/trends ---

  @Test
  @DisplayName("trends - 스냅샷이 없으면 빈 계열과 NOT_AVAILABLE 을 돌려준다")
  void trends_비어있음() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of());

    TrendListResponse response = seriesService.trends(USER_ID, "202607", 12);

    assertThat(response.trends()).isEmpty();
    assertThat(response.purchasingPowerStatus()).isEqualTo(IndexStatus.NOT_AVAILABLE.name());
    assertThat(response.marketValueStatus()).isEqualTo(IndexStatus.NOT_AVAILABLE.name());
  }

  @Test
  @DisplayName("trends - 오래된 달부터 차례로 싣고 요청 개월 수를 넘기지 않는다")
  void trends_오름차순_구간제한() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB),
                snapshot("202606", "95.40", "113.70", DecisionSignalCode.STAY),
                snapshot("202605", "96.00", "110.00", DecisionSignalCode.STAY)));

    TrendListResponse response = seriesService.trends(USER_ID, "202607", 2);

    assertThat(response.trends()).hasSize(2);
    assertThat(response.trends().get(0).yearMonth()).isEqualTo("202606");
    assertThat(response.trends().get(1).yearMonth()).isEqualTo("202607");
    assertThat(response.trends().get(1).purchasingPowerIndex()).isEqualTo(94.2);
  }

  @Test
  @DisplayName("trends - 소득·개인물가 계열은 PPI 도메인 미착수라 null 로 둔다")
  void trends_소득물가계열_미착수() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB)));

    TrendListResponse response = seriesService.trends(USER_ID, "202607", 12);

    TrendListResponse.TrendPoint point = response.trends().get(0);
    assertThat(point.purchasingPowerIndex()).isEqualTo(94.2);
    assertThat(point.incomeIndex()).isNull();
    assertThat(point.personalPriceIndex()).isNull();
    assertThat(point.nominalIncome()).isNull();
    assertThat(point.realIncome()).isNull();
  }

  // --- fixtures ---

  private IndexSnapshot snapshot(String ym, String ppi, String mvi, DecisionSignalCode signal) {
    return IndexSnapshot.builder()
        .userId(USER_ID)
        .baseYearMonth(ym)
        .dataVersion("20260701093000-000")
        .ruleVersion("1.2.0")
        .purchasingPowerIndex(ppi == null ? null : new BigDecimal(ppi))
        .marketValueIndex(mvi == null ? null : new BigDecimal(mvi))
        .decisionSignal(signal)
        .rationale(rationale())
        .build();
  }

  private IndexSnapshot snapshotWithVersions(
      String ym, String ppi, String ruleVersion, String dataVersion) {
    return IndexSnapshot.builder()
        .userId(USER_ID)
        .baseYearMonth(ym)
        .dataVersion(dataVersion)
        .ruleVersion(ruleVersion)
        .purchasingPowerIndex(new BigDecimal(ppi))
        .marketValueIndex(new BigDecimal("117.50"))
        .decisionSignal(DecisionSignalCode.SEEK_JOB)
        .rationale(rationale())
        .build();
  }

  private SnapshotRationale rationale() {
    return new SnapshotRationale(
        List.of("연봉 격차 15% 이상"),
        new BigDecimal("0.200000"),
        List.of("CustomSkillX"),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 1),
        "202606",
        "20260701093000-000");
  }
}
