package com.carix.index.dto;

/**
 * 소득 행이 있는 한 해의 말일 시점 연 소득 (Member A → Member C, US-D8).
 *
 * @param year 소득 행의 유효일 연도
 * @param annualGross 세전 연 소득. 연봉에 따로 받는 고정상여를 더한 값이고 단위는 원이다
 */
public record RecordedYearIncome(int year, long annualGross) {

  /** 금액을 가린다 — 완료 기준 10 이 금액이 로그에 남는 것을 금지한다. */
  @Override
  public String toString() {
    return "RecordedYearIncome[year=%d, annualGross=***]".formatted(year);
  }
}
