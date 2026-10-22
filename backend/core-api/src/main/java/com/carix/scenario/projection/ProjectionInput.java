package com.carix.scenario.projection;

/**
 * 48개월 투영 입력. 주식은 확정 합계에 넣지 않으므로 이 입력에 필드를 두지 않는다.
 *
 * @param currentAnnualGross 상여 반영 후 현재 세전 연봉. 실업급여 평균임금도 이 값이다
 * @param raiseRate 잔류 연 인상률 (0.03). 이직 후 연 인상률로도 쓴다
 * @param moveAnnualGross 상여 반영 후 이직 세전 연봉. null 이면 이직 없음
 * @param unemploymentMonths 이직 공백 개월수
 * @param oneTimeCost 이직 첫 달에 차감하는 일회성 비용
 * @param benefitEligible 실업급여 수급 자격
 * @param inflationRate 연 물가상승률 (0.026)
 * @param calibrationRatio 실수령 보정 비율. 잔류·이직 세후에 똑같이 곱한다
 */
public record ProjectionInput(
    long currentAnnualGross,
    double raiseRate,
    Long moveAnnualGross,
    int unemploymentMonths,
    long oneTimeCost,
    boolean benefitEligible,
    double inflationRate,
    double calibrationRatio) {}
