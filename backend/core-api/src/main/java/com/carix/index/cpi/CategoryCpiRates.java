package com.carix.index.cpi;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 카테고리별 CPI 전년 동월 대비 변화율.
 *
 * @param cpiMonth 사용한 CPI 기준월 {@code YYYYMM}
 * @param rates 계산 가능한 카테고리만 담긴다. 값은 비율이다 (0.05 = 5%)
 */
public record CategoryCpiRates(String cpiMonth, Map<ExpenseCategoryEnum, BigDecimal> rates) {

  public CategoryCpiRates {
    rates = Map.copyOf(rates);
  }
}
