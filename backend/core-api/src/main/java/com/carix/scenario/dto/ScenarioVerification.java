package com.carix.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 저장 시나리오의 입력 가정과 12개월 뒤 실제 소득의 비교 (기능 명세 US-S1, c21). 조회할 때 계산하고 저장하지 않는다. 상태에 해당하지 않는 필드는 {@code
 * null} 이다.
 *
 * @param monthsLeft {@code WAITING} 의 남은 개월(올림)
 * @param expectedRaisePercent {@code STAY_COMPARED} 의 예상 인상률(%)
 * @param actualRaisePercent {@code STAY_COMPARED} 의 실제 변화율(%)
 * @param offerAnnualGross {@code MOVE_COMPARED} 의 오퍼 세전 연 소득(원)
 * @param actualAnnualGross {@code MOVE_COMPARED} 의 실제 세전 연 소득(원)
 */
@Schema(description = "시나리오 사후 검증")
public record ScenarioVerification(
    Status status,
    @Schema(nullable = true) Integer monthsLeft,
    @Schema(nullable = true) Double expectedRaisePercent,
    @Schema(nullable = true) Double actualRaisePercent,
    @Schema(nullable = true) Long offerAnnualGross,
    @Schema(nullable = true) Long actualAnnualGross) {

  public enum Status {
    WAITING,
    NO_INCOME_AFTER,
    NO_OFFER,
    MOVE_COMPARED,
    NO_BASELINE,
    UNKNOWN_REASON,
    STAY_COMPARED
  }

  /** 금액을 가린다 — 완료 기준 10 이 금액이 로그에 남는 것을 금지한다. */
  @Override
  public String toString() {
    return "ScenarioVerification[status=%s, monthsLeft=%s]".formatted(status, monthsLeft);
  }
}
