package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.SnapshotHistoryResponse;
import com.carix.dashboard.dto.TrendListResponse;
import com.carix.index.dto.SkillContribution;
import com.carix.index.dto.SourceCoverage;
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
 * <p>셋 다 {@code index_snapshot} 이 원천이고, 행이 없으면 <b>임의 값 대신 빈 상태</b>를 돌려준다. 온보딩 직후처럼 재계산이 한 번도 돌지 않은
 * 사용자는 행이 없는 것이 정상이며(개발 계획 §4-2), 그때 0 이나 100 을 채우면 §5-G 가 철거한 스텁과 같은 죄가 된다.
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
  @DisplayName("summary - 저장된 원천별 커버리지로 metadata 를 채운다 (§5-M)")
  void summary_커버리지_저장분() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB)));

    DashboardSummaryResponse.SummaryMetadata metadata =
        summaryService.resolve(USER_ID, "202607").metadata();

    assertThat(metadata.dataVersion()).isEqualTo("20260701093000-000");
    assertThat(metadata.baseDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    // SARAMIN 40건(결측 42.5%) + JOBKOREA 60건(결측 20.0%)
    assertThat(metadata.sampleSize()).isEqualTo(100);
    assertThat(metadata.sources()).hasSize(2);
    // 표본 수로 가중 평균: (42.5×40 + 20.0×60) / 100 = 29.0
    assertThat(metadata.salaryMissingRate()).isEqualTo(29.0);
    // 표본이 가장 큰 원천의 안착 조건을 대표로 쓴다
    assertThat(metadata.peerGroup().fallbackLevel()).isEqualTo(3);
    assertThat(metadata.peerGroup().appliedConditions()).isEqualTo("서울 전체 / 경력 MID / 회사규모 무관");
    assertThat(metadata.peerGroup().excluded()).hasSize(1);
  }

  @Test
  @DisplayName("summary - 결측률을 남기지 않은 원천은 가중 평균의 분모에서도 빠진다")
  void summary_결측률_미기록원천() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshotWithUnmeasuredMissingRate()));

    DashboardSummaryResponse.SummaryMetadata metadata =
        summaryService.resolve(USER_ID, "202607").metadata();

    // 표본 수는 성공한 원천 전체를 센다
    assertThat(metadata.sampleSize()).isEqualTo(100);
    // 결측률을 아는 원천은 JOBKOREA 뿐이다. 20.0×60 / 60 = 20.0
    // SARAMIN 40건을 분모에만 남기면 12.0 이 되어, 모르는 값이 결측률을 낮춰 보이게 만든다
    assertThat(metadata.salaryMissingRate()).isEqualTo(20.0);
  }

  @Test
  @DisplayName("summary - 완화에 실패한 원천만 있으면 metadata 를 채우지 않는다")
  void summary_커버리지_실패만() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshotWithFailedCoverage()));

    DashboardSummaryResponse.SummaryMetadata metadata =
        summaryService.resolve(USER_ID, "202607").metadata();

    assertThat(metadata.sampleSize()).isNull();
    assertThat(metadata.salaryMissingRate()).isNull();
    assertThat(metadata.peerGroup()).isNull();
    assertThat(metadata.sources()).isEmpty();
  }

  @Test
  @DisplayName("summary - 근거 문장은 격차 구간 문구다 — 비율을 되살리지 않는다 (§5-L)")
  void summary_격차_구간문구() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB)));

    String rationale = summaryService.resolve(USER_ID, "202607").decisionSignal().rationale();

    assertThat(rationale).isEqualTo("공식 임금통계 중앙값보다 15% 이상 낮습니다.");
    assertThat(rationale).doesNotContain("%%");
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

  private IndexSnapshot snapshotWithFailedCoverage() {
    return IndexSnapshot.builder()
        .userId(USER_ID)
        .baseYearMonth("202607")
        .dataVersion("20260701093000-000")
        .ruleVersion("1.2.0")
        .purchasingPowerIndex(new BigDecimal("94.20"))
        .marketValueIndex(new BigDecimal("117.50"))
        .decisionSignal(DecisionSignalCode.SEEK_JOB)
        .rationale(
            new SnapshotRationale(
                List.of(),
                null,
                List.of(
                    new SourceCoverage("SARAMIN", 5, 4, false, "서울 전체", new BigDecimal("80.00"))),
                List.of(),
                List.of(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1),
                "202606",
                "20260701093000-000"))
        .build();
  }

  private IndexSnapshot snapshotWithUnmeasuredMissingRate() {
    return IndexSnapshot.builder()
        .userId(USER_ID)
        .baseYearMonth("202607")
        .dataVersion("20260701093000-000")
        .ruleVersion("1.2.0")
        .purchasingPowerIndex(new BigDecimal("94.20"))
        .marketValueIndex(new BigDecimal("117.50"))
        .decisionSignal(DecisionSignalCode.SEEK_JOB)
        .rationale(
            new SnapshotRationale(
                List.of(),
                "BELOW_MARKET_15_PLUS",
                List.of(
                    new SourceCoverage("SARAMIN", 4, 40, true, "서울 / 경력 MID±1", null),
                    new SourceCoverage("JOBKOREA", 3, 60, true, "서울 전체", new BigDecimal("20.00"))),
                List.of(),
                List.of(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1),
                "202606",
                "20260701093000-000"))
        .build();
  }

  private SnapshotRationale rationale() {
    return new SnapshotRationale(
        List.of("연봉 격차 15% 이상"),
        "BELOW_MARKET_15_PLUS",
        List.of(
            new SourceCoverage(
                "SARAMIN", 4, 40, true, "서울 / 경력 MID±1 / 회사규모 무관", new BigDecimal("42.50")),
            new SourceCoverage(
                "JOBKOREA", 3, 60, true, "서울 전체 / 경력 MID / 회사규모 무관", new BigDecimal("20.00"))),
        List.of(
            new SkillContribution(
                "Spring Boot",
                new BigDecimal("0.70"),
                new BigDecimal("120.0"),
                new BigDecimal("84.00"))),
        List.of("CustomSkillX"),
        LocalDate.of(2026, 7, 1),
        LocalDate.of(2026, 7, 1),
        "202606",
        "20260701093000-000");
  }
}
