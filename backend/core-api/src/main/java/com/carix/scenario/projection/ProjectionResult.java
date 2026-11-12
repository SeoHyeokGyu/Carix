package com.carix.scenario.projection;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 48개월 세후·실질 투영 결과. 금액은 모두 현재 가치 환산 후 원 단위로 반올림한다.
 *
 * @param stayRealChangePercent 잔류 1년차 세후를 물가로 나눈 값의 현재 세후 대비 변화(%)
 * @param stayCumulative 길이 48 월별 누적
 * @param moveCumulative 길이 48 월별 누적. 이직이 없으면 null
 * @param crossoverMonth 이직 누적이 잔류 누적 이상이 되는 첫 달. 48개월 안에 없거나 이직이 없으면 null
 * @param firstYear 12개월 누적 차이 분해. 이직이 없으면 null
 * @param equalizingRaisePercent 48개월 누적이 같아지는 잔류 인상률(%). 해가 없거나 이직이 없으면 null
 */
public record ProjectionResult(
    double stayRealChangePercent,
    long[] stayCumulative,
    long[] moveCumulative,
    Integer crossoverMonth,
    FirstYear firstYear,
    Double equalizingRaisePercent) {

  /**
   * 첫해 분해. {@code total == moveCumulative[11] - stayCumulative[11]} 이다.
   *
   * <p>{@code severance} 는 12개월째에 나갈 때 받을 세후·실질 퇴직금의 이직 − 잔류 차이다. 공백·실업급여·비용·퇴직금은 각자 반올림하고, 반올림
   * 오차(수 원)는 {@code salaryDiff} 가 흡수해 합이 정확히 맞는다.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FirstYear(
      long salaryDiff, long gap, long oneTimeCost, long benefit, long severance, long total) {}
}
