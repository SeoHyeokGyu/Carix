package com.carix.index.dto;

import com.carix.income.domain.IncomeChangeReasonEnum;
import java.time.LocalDate;

/**
 * 소득 행 하나의 유효일·연 소득·변경 사유 (Member A → Member C, c21).
 *
 * @param effectiveDate 소득 행의 유효일
 * @param annualGross 세전 연 소득. 연봉에 따로 받는 고정상여를 더한 값이고 단위는 원이다
 * @param changeReason 변경 사유. 없으면 {@code null}
 */
public record RecordedIncomeChange(
    LocalDate effectiveDate, long annualGross, IncomeChangeReasonEnum changeReason) {

  /** 금액을 가린다 — 완료 기준 10 이 금액이 로그에 남는 것을 금지한다. */
  @Override
  public String toString() {
    return "RecordedIncomeChange[effectiveDate=%s, annualGross=***, changeReason=%s]"
        .formatted(effectiveDate, changeReason);
  }
}
