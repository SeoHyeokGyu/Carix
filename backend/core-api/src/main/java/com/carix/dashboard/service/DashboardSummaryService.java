package com.carix.dashboard.service;

import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.DashboardSummaryResponse.DecisionSignal;
import com.carix.dashboard.dto.DashboardSummaryResponse.IndexDetail;
import com.carix.dashboard.dto.DashboardSummaryResponse.SummaryMetadata;
import com.carix.dashboard.dto.DashboardSummaryResponse.WageStatInfo;
import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.DecisionSignalCode;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.domain.SnapshotRationale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 지표 요약 (API 규격 §5.1, 화면 `S-10`).
 *
 * <p>스냅샷이 없으면 예외가 아니라 {@link IndexStatus#NOT_AVAILABLE} 이다. 값을 0 이나 100 으로 채우지 않는다 (개발 계획 §4-1,
 * [MVP 규격 §13] 데이터가 없는 기능을 추정값으로 채워 출시하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

  private static final String DIRECTION_INCREASED = "INCREASED";
  private static final String DIRECTION_DECREASED = "DECREASED";
  private static final String DIRECTION_FLAT = "FLAT";

  private final SnapshotQueryService snapshotQueryService;

  @Transactional(readOnly = true)
  public DashboardSummaryResponse resolve(UUID userId, String baseYearMonth) {
    List<IndexSnapshot> months = snapshotQueryService.latestPerMonthDesc(userId);
    Optional<IndexSnapshot> current = snapshotQueryService.forMonth(months, baseYearMonth);

    if (current.isEmpty()) {
      return empty();
    }

    IndexSnapshot snapshot = current.get();
    Optional<IndexSnapshot> previous = snapshotQueryService.previousOf(months, baseYearMonth);

    return new DashboardSummaryResponse(
        detail(
            snapshot.getPurchasingPowerIndex(),
            previous.map(IndexSnapshot::getPurchasingPowerIndex).orElse(null)),
        detail(
            snapshot.getMarketValueIndex(),
            previous.map(IndexSnapshot::getMarketValueIndex).orElse(null)),
        decisionSignal(snapshot),
        metadata(snapshot));
  }

  // --- 지수 ---

  private IndexDetail detail(BigDecimal value, BigDecimal previousValue) {
    if (value == null) {
      // 그 달에 계산이 불가했다. 사유를 더 좁히려면 rationale 에 상태가 남아야 한다 (§5-M).
      return new IndexDetail(null, null, null, IndexStatus.NOT_AVAILABLE.name(), null);
    }
    Double change =
        previousValue == null
            ? null
            : value.subtract(previousValue).setScale(2, RoundingMode.HALF_UP).doubleValue();

    return new IndexDetail(
        value.doubleValue(), change, direction(change), IndexStatus.OK.name(), null);
  }

  /** 전월이 없으면 방향도 없다. 0 을 넣으면 `변화 없음`으로 잘못 읽힌다. */
  private String direction(Double change) {
    if (change == null) {
      return null;
    }
    if (change > 0) {
      return DIRECTION_INCREASED;
    }
    return change < 0 ? DIRECTION_DECREASED : DIRECTION_FLAT;
  }

  // --- 결정 신호 ---

  private DecisionSignal decisionSignal(IndexSnapshot snapshot) {
    DecisionSignalCode code = snapshot.getDecisionSignal();
    SnapshotRationale rationale = snapshot.getRationale();

    return new DecisionSignal(
        code.name(),
        label(code),
        rationale == null ? List.of() : rationale.rulesApplied(),
        gapSentence(rationale));
  }

  private String label(DecisionSignalCode code) {
    return switch (code) {
      case SEEK_JOB -> "이직 탐색";
      case NEGOTIATE -> "협상 검토";
      case UPSKILL -> "스킬 보강";
      case STAY -> "현 상태 유지";
      case INSUFFICIENT_DATA -> "데이터 부족";
    };
  }

  /** 근거 문장. 격차 비율은 본인 데이터라 본인에게는 보여준다 — 다만 임금통계 중앙값과 함께 싣지 않는다 (기술 명세 §1.3: 둘이 함께 있으면 연봉이 복원된다). */
  private String gapSentence(SnapshotRationale rationale) {
    if (rationale == null || rationale.salaryGapRatio() == null) {
      return null;
    }
    BigDecimal percent =
        rationale
            .salaryGapRatio()
            .multiply(BigDecimal.valueOf(100))
            .setScale(1, RoundingMode.HALF_UP);
    return "공식 임금통계 중앙값 대비 연봉 격차가 %s%% 입니다."
        .formatted(percent.stripTrailingZeros().toPlainString());
  }

  // --- 메타데이터 ---

  private SummaryMetadata metadata(IndexSnapshot snapshot) {
    SnapshotRationale rationale = snapshot.getRationale();

    return new SummaryMetadata(
        rationale == null ? null : rationale.aggregateAsOf(),
        snapshot.getDataVersion(),
        snapshot.getRuleVersion(),
        false,
        // 아래 넷은 원천별 커버리지라 rationale 에 저장되지 않는다 (§5-M). 저장이 결정되기 전까지 비운다.
        List.of(),
        null,
        null,
        null,
        rationale == null ? null : new WageStatInfo(null, rationale.wageStatVintage()),
        null);
  }

  private DashboardSummaryResponse empty() {
    IndexDetail unavailable =
        new IndexDetail(null, null, null, IndexStatus.NOT_AVAILABLE.name(), null);

    return new DashboardSummaryResponse(
        unavailable,
        unavailable,
        new DecisionSignal(DecisionSignalCode.INSUFFICIENT_DATA.name(), "데이터 부족", List.of(), null),
        new SummaryMetadata(null, null, null, false, List.of(), null, null, null, null, null));
  }
}
