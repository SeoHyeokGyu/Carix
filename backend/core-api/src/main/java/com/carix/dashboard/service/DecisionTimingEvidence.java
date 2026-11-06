package com.carix.dashboard.service;

import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.DecisionSignalCode;
import com.carix.snapshot.domain.SnapshotRationale.OpeningWindowEvidence;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * 이직 탐색·협상 검토 판정 옆에 두는 공고 비중 변화 문장 (도메인 정의서 §6, c20). I/O 없이 저장된 근거만 읽는다.
 *
 * <p>판정 조건이 아니라 관측 사실이다. 그래서 {@code rulesApplied} 에 넣지 않고, 줄어든 경우도 똑같이 말하며, 인과를 주장하지 않는다.
 */
final class DecisionTimingEvidence {

  private static final Set<DecisionSignalCode> TARGET_SIGNALS =
      Set.of(DecisionSignalCode.SEEK_JOB, DecisionSignalCode.NEGOTIATE);
  private static final BigDecimal BASELINE_INDEX = new BigDecimal("100");

  /** 시장가치 신뢰 가드와 같은 1σ. 변화가 없는 달도 약 32% 는 통과하지만 문장은 관측값 그대로다. */
  private static final BigDecimal NOISE_MULTIPLIER = BigDecimal.ONE;

  private static final String YEAR_MONTH_PATTERN = "\\d{4}(0[1-9]|1[0-2])";

  private DecisionTimingEvidence() {}

  /**
   * @return 표시할 문장. 조건을 하나라도 못 넘으면 {@code null}
   */
  static String sentence(
      DecisionSignalCode code,
      BigDecimal openingWindowIndex,
      IndexStatus status,
      OpeningWindowEvidence evidence) {
    if (!TARGET_SIGNALS.contains(code)
        || status != IndexStatus.OK
        || openingWindowIndex == null
        || !hasAllMaterials(evidence)
        || !Boolean.TRUE.equals(evidence.windowsFullyCovered())) {
      return null;
    }
    BigDecimal change = openingWindowIndex.subtract(BASELINE_INDEX);
    if (change.abs().compareTo(evidence.standardError().multiply(NOISE_MULTIPLIER)) < 0) {
      return null;
    }
    BigDecimal baseline = evidence.baselineSharePercent().setScale(1, RoundingMode.HALF_UP);
    BigDecimal recent = evidence.recentSharePercent().setScale(1, RoundingMode.HALF_UP);
    int direction = change.signum();
    // 반올림한 비중으로 비교한다. 화면에 같은 숫자 둘이 「늘었습니다」로 나가면 모순이다.
    if (direction == 0 || direction != recent.compareTo(baseline)) {
      return null;
    }
    return "%s %s 공고 비중이 이전 12개월보다 %s (%s%% → %s%%)."
        .formatted(
            period(evidence.recentFromMonth(), evidence.recentToMonth()),
            evidence.maxFallbackLevel() >= 2 ? "인접 경력을 포함한 동료집단" : "내 동료집단",
            direction > 0 ? "늘었습니다" : "줄었습니다",
            baseline.toPlainString(),
            recent.toPlainString());
  }

  /** 이 필드들이 생기기 전 스냅샷 행은 여기서 조용히 걸러진다. */
  private static boolean hasAllMaterials(OpeningWindowEvidence evidence) {
    return evidence != null
        && evidence.recentSharePercent() != null
        && evidence.baselineSharePercent() != null
        && evidence.standardError() != null
        && evidence.maxFallbackLevel() != null
        && isYearMonth(evidence.recentFromMonth())
        && isYearMonth(evidence.recentToMonth());
  }

  private static boolean isYearMonth(String value) {
    return value != null && value.matches(YEAR_MONTH_PATTERN);
  }

  private static String period(String from, String to) {
    String fromYear = from.substring(0, 4);
    String toYear = to.substring(0, 4);
    return fromYear.equals(toYear)
        ? "%s년 %d~%d월".formatted(fromYear, month(from), month(to))
        : "%s년 %d월~%s년 %d월".formatted(fromYear, month(from), toYear, month(to));
  }

  private static int month(String yearMonth) {
    return Integer.parseInt(yearMonth.substring(4));
  }
}
