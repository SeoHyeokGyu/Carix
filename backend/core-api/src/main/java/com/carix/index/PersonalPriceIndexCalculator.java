package com.carix.index;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.service.MonthlyWeights;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * 개인물가 지수 = 기준월부터 Π(1 + Σ 그 달 가중치 × 카테고리 전월 대비 변화율) (기능 명세 §12.2).
 *
 * <p>구간 안에 한 달이라도 6개 카테고리 변화율이 다 있지 않으면 계산하지 않는다. 남은 가중치를 재조정하면 개인물가의 뜻이 조용히 바뀐다.
 */
public final class PersonalPriceIndexCalculator {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final MathContext PRECISION = MathContext.DECIMAL64;

  private PersonalPriceIndexCalculator() {}

  public static Optional<BigDecimal> accumulate(
      String baseYearMonth,
      String endYearMonth,
      Map<String, Map<ExpenseCategoryEnum, BigDecimal>> monthlyRates,
      Map<String, MonthlyWeights> monthlyWeights) {
    YearMonth end = YearMonth.parse(endYearMonth, YM);
    BigDecimal index = BigDecimal.ONE;
    for (YearMonth m = YearMonth.parse(baseYearMonth, YM).plusMonths(1);
        !m.isAfter(end);
        m = m.plusMonths(1)) {
      String key = YM.format(m);
      Map<ExpenseCategoryEnum, BigDecimal> rates = monthlyRates.get(key);
      MonthlyWeights weights = monthlyWeights.get(key);
      if (rates == null || weights == null || rates.size() != ExpenseCategoryEnum.values().length) {
        return Optional.empty();
      }
      BigDecimal change = BigDecimal.ZERO;
      for (ExpenseCategoryEnum c : ExpenseCategoryEnum.values()) {
        change = change.add(weights.weights().get(c).multiply(rates.get(c), PRECISION), PRECISION);
      }
      index = index.multiply(BigDecimal.ONE.add(change, PRECISION), PRECISION);
    }
    return Optional.of(index);
  }
}
