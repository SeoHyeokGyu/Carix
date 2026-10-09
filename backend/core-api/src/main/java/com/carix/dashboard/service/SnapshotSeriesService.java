package com.carix.dashboard.service;

import com.carix.dashboard.dto.SnapshotHistoryResponse;
import com.carix.dashboard.dto.TrendListResponse;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스냅샷 시계열 두 화면 — `S-11` 이력(`/dashboard/history`, 당시 판정(그 달 끝 기준)과 현재 규칙 값을 함께 싣는다)과 `S-14`
 * 추이(`/dashboard/trends`).
 *
 * <p>둘 다 스냅샷이 없으면 빈 결과를 돌려준다. 화면은 그때 `다음 달부터 변화를 볼 수 있습니다` 같은 비어있음 상태를 그린다 (기능 명세 §5).
 */
@Service
@RequiredArgsConstructor
public class SnapshotSeriesService {

  private final SnapshotQueryService snapshotQueryService;

  /** `S-11` 이력. 최신월부터 내려간다. */
  @Transactional(readOnly = true)
  public SnapshotHistoryResponse history(UUID userId) {
    List<SnapshotHistoryResponse.SnapshotPoint> points = new ArrayList<>();
    for (SnapshotQueryService.MonthSnapshots month :
        snapshotQueryService.historyPerMonthDesc(userId)) {
      points.add(toPoint(month));
    }
    return new SnapshotHistoryResponse(points);
  }

  /**
   * `S-14` 추이. 차트는 왼쪽이 과거라 오름차순으로 싣는다.
   *
   * <p>{@code incomeIndex}·{@code personalPriceIndex}·{@code nominalIncome}·{@code realIncome} 은
   * 채우지 않는다 — {@code index_snapshot} 에 그 컬럼이 없고 원천인 PPI 도메인(Member A)이 아직 없다. 0 으로 채우면 차트가 있지도 않은
   * 하락을 그린다.
   */
  @Transactional(readOnly = true)
  public TrendListResponse trends(UUID userId, String baseYearMonth, int durationMonths) {
    List<IndexSnapshot> months = snapshotQueryService.latestPerMonthDesc(userId);

    List<IndexSnapshot> window = new ArrayList<>();
    for (IndexSnapshot snapshot : months) {
      if (snapshot.getBaseYearMonth().compareTo(baseYearMonth) > 0) {
        continue;
      }
      if (window.size() >= durationMonths) {
        break;
      }
      window.add(snapshot);
    }
    Collections.reverse(window);

    List<TrendListResponse.TrendPoint> points = new ArrayList<>();
    for (IndexSnapshot snapshot : window) {
      points.add(
          new TrendListResponse.TrendPoint(
              snapshot.getBaseYearMonth(),
              toDouble(snapshot.getPurchasingPowerIndex()),
              toDouble(snapshot.getMarketValueIndex()),
              toDouble(snapshot.getOpeningWindowIndex()),
              null,
              null,
              null,
              null));
    }

    return new TrendListResponse(
        points,
        seriesStatus(points, TrendListResponse.TrendPoint::purchasingPowerIndex),
        seriesStatus(points, TrendListResponse.TrendPoint::marketValueIndex),
        seriesStatus(points, TrendListResponse.TrendPoint::openingWindowIndex));
  }

  // --- 매핑 ---

  private SnapshotHistoryResponse.SnapshotPoint toPoint(SnapshotQueryService.MonthSnapshots month) {
    IndexSnapshot snapshot = month.current();
    IndexSnapshot recorded = month.recorded();

    return new SnapshotHistoryResponse.SnapshotPoint(
        snapshot.getBaseYearMonth(),
        toDouble(snapshot.getPurchasingPowerIndex()),
        toDouble(snapshot.getMarketValueIndex()),
        toDouble(snapshot.getOpeningWindowIndex()),
        snapshot.getDecisionSignal().name(),
        snapshot.getDecisionSignal().label(),
        snapshot.getDataVersion(),
        snapshot.getRuleVersion(),
        toRationale(snapshot.getRationale()),
        recorded == null ? null : toRecorded(recorded),
        recorded != null && recorded.getDecisionSignal() != snapshot.getDecisionSignal());
  }

  private SnapshotHistoryResponse.RecordedSnapshot toRecorded(IndexSnapshot recorded) {
    return new SnapshotHistoryResponse.RecordedSnapshot(
        toDouble(recorded.getPurchasingPowerIndex()),
        toDouble(recorded.getMarketValueIndex()),
        toDouble(recorded.getOpeningWindowIndex()),
        recorded.getDecisionSignal().name(),
        recorded.getDecisionSignal().label(),
        recorded.getDataVersion(),
        recorded.getRuleVersion(),
        toRationale(recorded.getRationale()),
        recorded.getCreatedAt());
  }

  private SnapshotHistoryResponse.SnapshotRationale toRationale(SnapshotRationale rationale) {
    return rationale == null
        ? null
        : new SnapshotHistoryResponse.SnapshotRationale(
            rationale.rulesApplied(), rationale.excludedSkills(), rationale.aggregateAsOf());
  }

  /** 계열에 값이 하나도 없으면 화면이 이유를 알아야 한다. 한 달이라도 있으면 {@code OK} 이고 결측월은 점의 {@code null} 이 말한다. */
  private String seriesStatus(
      List<TrendListResponse.TrendPoint> points,
      Function<TrendListResponse.TrendPoint, Double> series) {
    boolean anyValue = points.stream().anyMatch(point -> series.apply(point) != null);
    return anyValue ? IndexStatus.OK.name() : IndexStatus.NOT_AVAILABLE.name();
  }

  private Double toDouble(BigDecimal value) {
    return value == null ? null : value.doubleValue();
  }
}
