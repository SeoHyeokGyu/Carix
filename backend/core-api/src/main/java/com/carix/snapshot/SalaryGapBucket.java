package com.carix.snapshot;

import java.math.BigDecimal;

/**
 * 소득 격차 비율의 저장 구간 (개발 계획 §5-L).
 *
 * <p>비율 자체를 소수점 6자리로 {@code rationale} 에 남기면 평문 {@code wage_stat_observation.wage_median} · 평문
 * {@code career_profile} 동료집단과 조인해 {@code 연봉 = 중앙값 / (1 + 격차비율)} 로 원 단위까지 복원된다. {@code
 * income_history} 를 AES-256-GCM 으로 감싼 이유가 사라진다.
 *
 * <p>경계는 결정 규칙표의 임계값과 같다(-0.05 / 0.05 / 0.15). {@code rulesApplied} 가 이미 같은 구간을 문구로 담고 있어 구간 저장이 화면
 * 근거에서 정보를 더 잃지 않는다 — 6자리는 복원 정밀도만 더했다.
 *
 * <p>격차는 {@code (중앙값 - 현재연봉) / 현재연봉} 이라 <b>양수면 시장보다 덜 받는다</b>는 뜻이다.
 */
public enum SalaryGapBucket {

  /** 중앙값 대비 15% 이상 낮다. 규칙 2(이직 탐색) 구간. */
  BELOW_MARKET_15_PLUS("공식 임금통계 중앙값보다 15% 이상 낮습니다"),

  /** 중앙값 대비 5~15% 낮다. 규칙 3(협상 검토) 구간. */
  BELOW_MARKET_5_TO_15("공식 임금통계 중앙값보다 5~15% 낮습니다"),

  /** 중앙값 ±5% 안. 시장 수준이다. */
  NEAR_MARKET("공식 임금통계 중앙값과 비슷한 수준입니다"),

  /** 중앙값 대비 5% 이상 높다. */
  ABOVE_MARKET_5_PLUS("공식 임금통계 중앙값보다 5% 이상 높습니다");

  private static final BigDecimal SEEK_JOB = new BigDecimal("0.15");
  private static final BigDecimal NEGOTIATE = new BigDecimal("0.05");
  private static final BigDecimal STAY_LOWER = new BigDecimal("-0.05");

  private final String label;

  SalaryGapBucket(String label) {
    this.label = label;
  }

  /** 화면 근거 아코디언에 그대로 쓰는 문구. 금액을 담지 않는다. */
  public String label() {
    return label;
  }

  /**
   * 격차 비율을 구간으로 접는다.
   *
   * @param salaryGapRatio 격차 비율. 산출 불가면 {@code null}
   * @return 구간. 입력이 {@code null} 이면 {@code null} — 0 이나 {@link #NEAR_MARKET} 으로 채우지 않는다
   */
  public static SalaryGapBucket of(BigDecimal salaryGapRatio) {
    if (salaryGapRatio == null) {
      return null;
    }
    if (salaryGapRatio.compareTo(SEEK_JOB) >= 0) {
      return BELOW_MARKET_15_PLUS;
    }
    if (salaryGapRatio.compareTo(NEGOTIATE) >= 0) {
      return BELOW_MARKET_5_TO_15;
    }
    return salaryGapRatio.compareTo(STAY_LOWER) > 0 ? NEAR_MARKET : ABOVE_MARKET_5_PLUS;
  }
}
