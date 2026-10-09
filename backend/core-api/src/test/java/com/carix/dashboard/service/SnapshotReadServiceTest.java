package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.SnapshotHistoryResponse;
import com.carix.dashboard.dto.TrendListResponse;
import com.carix.index.dto.SkillContribution;
import com.carix.index.dto.SourceCoverage;
import com.carix.index.dto.TopPriceDriver;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.DecisionSignalCode;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 스냅샷을 읽는 세 화면 API 검증 — `/dashboard/summary`, `/history`, `/trends`.
 *
 * <p>셋 다 {@code index_snapshot} 이 원천이고, 행이 없으면 <b>임의 값 대신 빈 상태</b>를 돌려준다. 온보딩 직후처럼 재계산이 한 번도 돌지 않은
 * 사용자는 행이 없는 것이 정상이며(기술 명세 §1.3), 그때 0 이나 100 을 채우면 MVP 범위 판별로 철거한 스텁과 같은 죄가 된다 (개발 계획 §4).
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
  @DisplayName("summary - 저장된 원천별 커버리지로 metadata 를 채운다 (기술 명세 §1.3)")
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
  @DisplayName("summary - 근거 문장은 격차 구간 문구다 — 비율을 되살리지 않는다 (기술 명세 §1.3)")
  void summary_격차_구간문구() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202607", "94.20", "117.50", DecisionSignalCode.SEEK_JOB)));

    String rationale = summaryService.resolve(USER_ID, "202607").decisionSignal().rationale();

    assertThat(rationale).isEqualTo("공식 임금통계 중앙값보다 15% 이상 낮습니다.");
    assertThat(rationale).doesNotContain("%%");
  }

  /**
   * D3 — 값이 있으면 상태는 값을 갖는 상태({@code OK}·{@code INSUFFICIENT_HISTORY})다. 값을 갖지 않는 상태가 값과 함께 저장된 옛 행은
   * {@code OK} 로 접는다. 프런트 계약이 "그 둘이 아니면 value 는 null" 이다.
   */
  @Test
  @DisplayName("summary - 값이 있는데 저장된 상태가 값 없는 상태(UPDATE_DELAYED)면 OK 로 접힌다")
  void summary_값이있으면_상태는_OK() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(
                    new BigDecimal("94.20"),
                    null,
                    null,
                    new SnapshotRationale.IndexStatuses(
                        IndexStatus.UPDATE_DELAYED, IndexStatus.OK, IndexStatus.OK),
                    null)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.purchasingPowerIndex().indexStatus()).isEqualTo(IndexStatus.OK.name());
  }

  /** 구매력 지수가 기준월부터 12개월 미만이면 값과 함께 비교 기간 짧음을 그대로 보낸다 — 화면이 「비교 기간 짧음」을 그린다. */
  @Test
  @DisplayName("summary - 값이 있고 저장된 상태가 INSUFFICIENT_HISTORY 면 그대로 나간다")
  void summary_비교기간짧음은_값과_함께_그대로() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(
                    new BigDecimal("98.00"),
                    null,
                    null,
                    new SnapshotRationale.IndexStatuses(
                        IndexStatus.INSUFFICIENT_HISTORY, IndexStatus.OK, IndexStatus.OK),
                    null)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.purchasingPowerIndex().value()).isEqualTo(98.0);
    assertThat(response.purchasingPowerIndex().indexStatus())
        .isEqualTo(IndexStatus.INSUFFICIENT_HISTORY.name());
  }

  /** D2 — 표본 부족을 계산 대기로 뭉개지 않는다. */
  @Test
  @DisplayName("summary - 값이 없으면 저장된 상태가 그대로 나온다")
  void summary_값이없으면_저장된_상태() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(
                    null,
                    null,
                    null,
                    new SnapshotRationale.IndexStatuses(
                        IndexStatus.NOT_AVAILABLE, IndexStatus.INSUFFICIENT_SAMPLE, null),
                    null)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.marketValueIndex().indexStatus())
        .isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE.name());
  }

  /**
   * 운영에 {@code opening_window_index} 가 {@code NULL} 인데 상태는 {@code OK} 인 옛 행이 남아 있다(경위 미규명). 그대로 실으면
   * 화면이 "산출됨, 숫자 없음"을 그리고 프런트 {@code lib/api/dashboard.ts} 가 문서화한 계약이 뒤집힌다.
   */
  @Test
  @DisplayName("summary - 값을 요구하는 상태인데 값이 없는 옛 행은 계산 대기로 접는다")
  void summary_값없는_OK_는_접힌다() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(
                    null,
                    null,
                    null,
                    new SnapshotRationale.IndexStatuses(
                        IndexStatus.INSUFFICIENT_HISTORY,
                        IndexStatus.INSUFFICIENT_SAMPLE,
                        IndexStatus.OK),
                    null)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.openingWindowIndex().value()).isNull();
    assertThat(response.openingWindowIndex().indexStatus())
        .isEqualTo(IndexStatus.NOT_AVAILABLE.name());
    assertThat(response.purchasingPowerIndex().indexStatus())
        .isEqualTo(IndexStatus.NOT_AVAILABLE.name());
    assertThat(response.marketValueIndex().indexStatus())
        .isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE.name());
  }

  @Test
  @DisplayName("summary - 상태를 저장하지 않은 옛 행은 계산 대기로 읽힌다")
  void summary_옛행은_계산대기() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(null, null, null, SnapshotRationale.IndexStatuses.unknown(), null)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.purchasingPowerIndex().indexStatus())
        .isEqualTo(IndexStatus.NOT_AVAILABLE.name());
  }

  @Test
  @DisplayName("summary - 개방창 근거 셋이 메타데이터에 실린다")
  void summary_개방창_근거() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(
                    null,
                    null,
                    new BigDecimal("118.40"),
                    new SnapshotRationale.IndexStatuses(null, null, IndexStatus.OK),
                    new SnapshotRationale.OpeningWindowEvidence(
                        124, 86, 34, new BigDecimal("21.50"), 3, "서울 전체 / MID", 29))));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.openingWindowIndex().value()).isEqualTo(118.4);
    assertThat(response.metadata().openingWindow())
        .isEqualTo(new DashboardSummaryResponse.OpeningWindowSummary(34, 21.5, 86));
  }

  @Test
  @DisplayName("summary - 개방창 근거가 없으면 메타데이터도 비운다")
  void summary_개방창_근거없음() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(null, null, null, SnapshotRationale.IndexStatuses.unknown(), null)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.metadata().openingWindow()).isNull();
  }

  /** 스냅샷이 아예 없는 사용자. 지수 셋이 다 NOT_AVAILABLE 이어야 S-10 빈 상태가 뜬다. */
  @Test
  @DisplayName("summary - 스냅샷이 없으면 지수 셋이 모두 계산 대기다")
  void summary_스냅샷없음_지수셋() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of());

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.purchasingPowerIndex().indexStatus())
        .isEqualTo(IndexStatus.NOT_AVAILABLE.name());
    assertThat(response.marketValueIndex().indexStatus())
        .isEqualTo(IndexStatus.NOT_AVAILABLE.name());
    assertThat(response.openingWindowIndex().indexStatus())
        .isEqualTo(IndexStatus.NOT_AVAILABLE.name());
  }

  @Test
  @DisplayName("summary - 저장된 물가 기여도 1위를 기여도 값만 빼고 메타데이터에 싣는다")
  void summary_물가기여도() {
    var driver =
        new TopPriceDriver("TRANSPORT", "교통 부담률 최대 상승", true, "202606", new BigDecimal("0.005"));
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshotWithDriver(driver)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.metadata().topPriceDriver())
        .isEqualTo(
            new DashboardSummaryResponse.PriceDriver("TRANSPORT", "교통 부담률 최대 상승", true, "202606"));
  }

  @Test
  @DisplayName("summary - 근거에 물가 기여도가 없으면(옛 행 포함) 메타데이터도 비운다")
  void summary_물가기여도없음() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshotWithDriver(null)));

    DashboardSummaryResponse response = summaryService.resolve(USER_ID, "202607");

    assertThat(response.metadata().topPriceDriver()).isNull();
  }

  private IndexSnapshot snapshotWithDriver(TopPriceDriver driver) {
    return IndexSnapshot.builder()
        .userId(USER_ID)
        .baseYearMonth("202607")
        .dataVersion("20260701093000-000")
        .ruleVersion("1.2.0")
        .purchasingPowerIndex(new BigDecimal("100.00"))
        .decisionSignal(DecisionSignalCode.STAY)
        .rationale(
            new SnapshotRationale(
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1),
                "202606",
                "20260701093000-000",
                SnapshotRationale.IndexStatuses.unknown(),
                null,
                null,
                driver))
        .build();
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

  @Test
  @DisplayName("history - 결정 신호의 한글 문구를 코드와 함께 싣는다")
  void history_라벨() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(snapshot("202609", "101.00", "108.00", DecisionSignalCode.UPSKILL)));

    SnapshotHistoryResponse response = seriesService.history(USER_ID);

    assertThat(response.snapshots()).hasSize(1);
    assertThat(response.snapshots().get(0).decisionSignal()).isEqualTo("UPSKILL");
    assertThat(response.snapshots().get(0).label()).isEqualTo("스킬 보강 우선");
  }

  private IndexSnapshot snapshotAt(
      String month, String dataVersion, DecisionSignalCode signal, String createdAtIso) {
    return IndexSnapshot.builder()
        .userId(USER_ID)
        .baseYearMonth(month)
        .dataVersion(dataVersion)
        .ruleVersion("1.5.0")
        .purchasingPowerIndex(new BigDecimal("100.00"))
        .decisionSignal(signal)
        .createdAt(createdAtIso == null ? null : OffsetDateTime.parse(createdAtIso))
        .build();
  }

  @Test
  @DisplayName("history - 당시 판정은 다음 달 1일 00:00 UTC 전에 만든 행 중 최신 버전이다")
  void recordedPicksLatestBeforeMonthEnd() {
    IndexSnapshot inMonth =
        snapshotAt("202609", "20260915000000-000", DecisionSignalCode.STAY, "2026-09-15T00:00:00Z");
    IndexSnapshot later =
        snapshotAt(
            "202609", "20261009000000-000", DecisionSignalCode.SEEK_JOB, "2026-10-09T00:00:00Z");
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(inMonth, later));

    var point = seriesService.history(USER_ID).snapshots().get(0);

    assertThat(point.decisionSignal()).isEqualTo("SEEK_JOB");
    assertThat(point.recorded().decisionSignal()).isEqualTo("STAY");
    assertThat(point.recorded().dataVersion()).isEqualTo("20260915000000-000");
    assertThat(point.changed()).isTrue();
  }

  @Test
  @DisplayName("history - 서울 기준 다음 달 1일 새벽(UTC 전월 말)에 만든 행은 전월 당시 판정이다")
  void recordedUsesUtcMonthEnd() {
    IndexSnapshot seoulOct1 =
        snapshotAt(
            "202609", "20260930233000-000", DecisionSignalCode.STAY, "2026-10-01T08:30:00+09:00");
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of(seoulOct1));

    assertThat(seriesService.history(USER_ID).snapshots().get(0).recorded()).isNotNull();
  }

  @Test
  @DisplayName("history - recordedAt 은 UTC 오프셋으로 싣는다")
  void recordedAtIsUtc() {
    IndexSnapshot seoul =
        snapshotAt(
            "202609", "20260915000000-000", DecisionSignalCode.STAY, "2026-09-15T12:00:00+09:00");
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of(seoul));

    var recordedAt = seriesService.history(USER_ID).snapshots().get(0).recorded().recordedAt();
    assertThat(recordedAt.getOffset()).isEqualTo(java.time.ZoneOffset.UTC);
    assertThat(recordedAt.getHour()).isEqualTo(3);
  }

  @Test
  @DisplayName("history - 다음 달 1일 00:00:00 UTC 정각에 만든 행은 그 달 당시 판정이 아니다")
  void excludesRowAtBoundary() {
    IndexSnapshot atBoundary =
        snapshotAt("202609", "20261001000000-000", DecisionSignalCode.STAY, "2026-10-01T00:00:00Z");
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of(atBoundary));

    var point = seriesService.history(USER_ID).snapshots().get(0);
    assertThat(point.recorded()).isNull();
    assertThat(point.changed()).isFalse();
  }

  @Test
  @DisplayName("history - created_at 이 없는 행은 당시 판정 후보가 아니다")
  void nullCreatedAtIsNotRecorded() {
    IndexSnapshot noTime =
        snapshotAt("202609", "20260915000000-000", DecisionSignalCode.STAY, null);
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of(noTime));

    assertThat(seriesService.history(USER_ID).snapshots().get(0).recorded()).isNull();
  }

  @Test
  @DisplayName("history - 신호가 같고 지수만 다르면 changed 는 false 다")
  void changedOnlyOnSignal() {
    IndexSnapshot inMonth =
        snapshotAt("202609", "20260915000000-000", DecisionSignalCode.STAY, "2026-09-15T00:00:00Z");
    IndexSnapshot later =
        snapshotAt("202609", "20261009000000-000", DecisionSignalCode.STAY, "2026-10-09T00:00:00Z");
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(inMonth, later));

    var point = seriesService.history(USER_ID).snapshots().get(0);
    assertThat(point.recorded()).isNotNull();
    assertThat(point.changed()).isFalse();
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

  @Test
  @DisplayName("trends - 개방창 계열과 계열 상태를 싣는다")
  void trends_개방창_계열() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(
                    null,
                    null,
                    new BigDecimal("118.40"),
                    new SnapshotRationale.IndexStatuses(null, null, IndexStatus.OK),
                    null)));

    TrendListResponse response = seriesService.trends(USER_ID, "202607", 12);

    assertThat(response.trends())
        .extracting(TrendListResponse.TrendPoint::openingWindowIndex)
        .containsExactly(118.4);
    assertThat(response.openingWindowStatus()).isEqualTo(IndexStatus.OK.name());
  }

  /** 계열에 값이 하나도 없으면 화면이 이유를 알아야 한다. */
  @Test
  @DisplayName("trends - 개방창 값이 한 달도 없으면 계열 상태가 계산 대기다")
  void trends_개방창_전부결측() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(null, null, null, SnapshotRationale.IndexStatuses.unknown(), null)));

    TrendListResponse response = seriesService.trends(USER_ID, "202607", 12);

    assertThat(response.openingWindowStatus()).isEqualTo(IndexStatus.NOT_AVAILABLE.name());
  }

  @Test
  @DisplayName("history - 개방창 지수를 싣는다")
  void history_개방창_지수() {
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                snapshotWith(
                    null,
                    null,
                    new BigDecimal("118.40"),
                    new SnapshotRationale.IndexStatuses(null, null, IndexStatus.OK),
                    null)));

    SnapshotHistoryResponse response = seriesService.history(USER_ID);

    assertThat(response.snapshots())
        .extracting(SnapshotHistoryResponse.SnapshotPoint::openingWindowIndex)
        .containsExactly(118.4);
  }

  // --- fixtures ---

  /** 기존 {@code snapshot(...)} 은 지수 둘만 받는다. 상태·근거까지 꽂아야 하는 테스트만 이쪽을 쓴다. */
  private IndexSnapshot snapshotWith(
      BigDecimal ppi,
      BigDecimal mvi,
      BigDecimal owi,
      SnapshotRationale.IndexStatuses statuses,
      SnapshotRationale.OpeningWindowEvidence evidence) {
    return IndexSnapshot.builder()
        .userId(USER_ID)
        .baseYearMonth("202607")
        .dataVersion("20260701093000-000")
        .ruleVersion("1.2.0")
        .purchasingPowerIndex(ppi)
        .marketValueIndex(mvi)
        .openingWindowIndex(owi)
        .decisionSignal(DecisionSignalCode.SEEK_JOB)
        .rationale(
            new SnapshotRationale(
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 1),
                "202606",
                "20260701093000-000",
                statuses,
                evidence))
        .build();
  }

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
                "20260701093000-000",
                null,
                null))
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
                "20260701093000-000",
                null,
                null))
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
        "20260701093000-000",
        null,
        null);
  }
}
