package com.carix.wagestat;

/**
 * 임금통계 관측치의 출처.
 *
 * <p>KOSIS 실측과 {@code FALLBACK_MEDIAN_BY_BRACKET} 하드코딩 상수가 같은 컬럼에 섞이면, 여러 해를 적재했을 때 상수가 만든 상승률 0% 의
 * 평평한 계열이 실측처럼 읽힌다. 연도 시계열 계산은 {@link #KOSIS} 행만 쓴다.
 */
public enum WageStatSource {
  /** KOSIS 응답을 24개 임금계층 누적분포로 보간해 얻은 실측. */
  KOSIS,

  /** 하드코딩 상수. 실측이 아니며 상승률 계산에서 제외된다. */
  FALLBACK,

  /** 출처 컬럼 도입 이전 적재분. 쓰기 경로는 이 값을 만들지 않는다 — DB 기본값으로만 존재한다. */
  UNKNOWN
}
