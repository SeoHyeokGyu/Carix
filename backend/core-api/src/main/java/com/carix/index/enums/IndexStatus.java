package com.carix.index.enums;

/**
 * 지수 산출 결과의 상태. 값이 없는 이유를 화면이 서로 다르게 그려야 하므로 하나로 뭉치지 않는다 ({@code
 * index_snapshot.purchasing_power_index} DDL 주석 — 계산 불가면 NULL 이고 0 이나 100 으로 채우지 않는다).
 */
public enum IndexStatus {
  /** 정상 산출. {@code value} 가 있다. */
  OK,

  /**
   * 표본 부족. MVI·OWI 가 쓴다.
   *
   * <p>MVI 는 동료집단 셀 N &lt; 10, 계산 가능 스킬 0개, 그리고 남은 스킬이 전부 {@code NO_RECENT_DEMAND} 여서 가중합이 0 인 경우다
   * — 0 은 기준 100 지수에서 값이 아니다 (기능 명세 §4.1·§4.2). OWI 는 마감일 보유 표본 N &lt; 10 이거나 전체 시장 중앙 개방일수를 못 구한
   * 경우다 (기능 명세 §4.5).
   */
  INSUFFICIENT_SAMPLE,

  /**
   * 원천 데이터가 늦었다. PPI 는 CPI 최신월 결측, MVI 는 집계 지연이다. 보간하지 않고 마지막 계산월을 쓴다 (§4.3).
   *
   * <p>PPI 에서는 최신월 발행 지연에만 해당한다 — 과거월의 CPI 결측은 이 상태가 아니라 {@link #NOT_AVAILABLE} 이다. 최신월만 스킵 대상이라,
   * 과거월은 이 값이어도 행을 만들고 지수를 {@code NULL} 로, 결정 신호를 데이터 부족으로 기록한다.
   */
  UPDATE_DELAYED,

  /** 비교 기간 짧음. PPI 전용 — 기준월부터 12개월 미만이라 값은 있되 비교가 짧다 (§4.3). 이 경우에만 값이 있으면서 OK 가 아니다. */
  INSUFFICIENT_HISTORY,

  /** 계산에 필요한 입력 자체가 없다. 온보딩 미완료거나 산출기가 아직 스텁이다. */
  NOT_AVAILABLE
}
