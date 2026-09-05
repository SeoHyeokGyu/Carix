package com.carix.index.enums;

/**
 * 시장가치 지수 가중합에서 스킬이 빠진 이유. 제외 스킬의 가중치는 나머지에 재정규화한다 (기능 명세 §4.2).
 *
 * <p>C 의 결정 신호 엔진과 근거 아코디언이 사유별로 다르게 분기하므로 자유 문자열이 아니라 열거형이다.
 */
public enum SkillExclusionReason {
  /** 사용자 정의 스킬이라 시장 집계에 매칭이 없다. 화면 표기는 `통계 미제공` (§4.2). */
  CUSTOM_SKILL_NO_STATS,

  /**
   * 스킬 행 표본이 기간 창별 기준({@code SkillDemandCalculator.MIN_SKILL_SAMPLE_SIZE}) 미달이다. 모든 원천에서 미달일 때만 이
   * 사유가 붙는다 — 한 원천이라도 통과했으면 그 원천으로 결합한다 (기술 명세 §2.1).
   */
  INSUFFICIENT_SAMPLE,

  /** 이전 12개월(4~15개월 전) 관측이 없어 분모가 0이다. 화면 표기는 `신규 관측` (§4.1). */
  NEW_OBSERVATION,

  /** 최근 3개월 관측이 없다. 지수 0이 아니라 별도 상태이고 가중합에는 0으로 들어간다 (§4.1). */
  NO_RECENT_DEMAND
}
