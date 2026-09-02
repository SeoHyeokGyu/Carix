package com.carix.index.enums;

/**
 * 지수 산출 결과의 상태. 값이 없는 이유를 화면이 서로 다르게 그려야 하므로 하나로 뭉치지 않는다 ({@code
 * index_snapshot.purchasing_power_index} DDL 주석 — 계산 불가면 NULL 이고 0 이나 100 으로 채우지 않는다).
 */
public enum IndexStatus {
  /** 정상 산출. {@code value} 가 있다. */
  OK,

  /** 표본 부족. MVI 전용 — 동료집단 셀 N &lt; 10 이거나 계산 가능 스킬이 0개다 (기능 명세 §4.2). */
  INSUFFICIENT_SAMPLE,

  /**
   * 원천 데이터가 늦었다. PPI 는 CPI 최신월 결측, MVI 는 집계 지연이다. 보간하지 않고 마지막 계산월을 쓴다 (§4.3).
   *
   * <p>PPI 에서는 최신월 발행 지연에만 해당한다 — 과거월의 CPI 결측은 이 상태가 아니라 {@link #NOT_AVAILABLE} 이다. 최신월만 스킵 대상이라,
   * 과거월은 이 값이어도 행을 만들고 지수를 {@code NULL} 로, 결정 신호를 데이터 부족으로 기록한다.
   */
  UPDATE_DELAYED,

  /** 비교 기간 부족. PPI 전용 — 소득 이력이 1건뿐이라 지수가 100 단일점이다 (§4.3). 이 경우에만 값이 있으면서 OK 가 아니다. */
  INSUFFICIENT_HISTORY,

  /** 계산에 필요한 입력 자체가 없다. 온보딩 미완료거나 산출기가 아직 스텁이다. */
  NOT_AVAILABLE
}
