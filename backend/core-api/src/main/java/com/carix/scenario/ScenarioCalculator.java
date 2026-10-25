package com.carix.scenario;

import org.springframework.stereotype.Component;

/** 결과 유형 판정과 엔진 1 저장분의 실업급여 안내 문구. 계산은 {@code StayMoveProjector} 가 한다. */
@Component
public class ScenarioCalculator {

  private static final String GAIN = "ESTIMATED_GAIN";
  private static final String OPPORTUNITY_COST = "ESTIMATED_OPPORTUNITY_COST";

  private final UnemploymentBenefitAssumption assumption;

  public ScenarioCalculator(UnemploymentBenefitAssumption assumption) {
    this.assumption = assumption;
  }

  /** 규격이 고정한 결과 유형. 차이가 정확히 0이면 추가보상 쪽이다. */
  public static String typeOf(long difference) {
    return difference >= 0 ? GAIN : OPPORTUNITY_COST;
  }

  /** 화면 표기. 규격이 이 두 문구로 고정한다 — 어느 쪽이 유리한지 말하되 `추정` 을 붙여 보장된 손익으로 읽히지 않게 한다. */
  public static String typeLabelOf(long difference) {
    return difference >= 0 ? "옮기면 추정 이득" : "남으면 추정 이득";
  }

  /** 엔진 1 저장분을 되살릴 때 쓴다. 기준 시점은 항상 <b>현재</b> 설정값이다. */
  public String benefitNote(boolean eligibleForBenefit) {
    return eligibleForBenefit
        ? "가정 기반 추정 (기준 시점: %s)".formatted(assumption.assumptionAsOf())
        : "실업급여 미반영";
  }
}
