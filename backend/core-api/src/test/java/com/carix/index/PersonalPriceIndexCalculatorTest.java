package com.carix.index;

import static com.carix.expense.domain.ExpenseCategoryEnum.FOOD;
import static com.carix.expense.domain.ExpenseCategoryEnum.HOUSING;
import static org.assertj.core.api.Assertions.assertThat;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.WeightStatusEnum;
import com.carix.expense.service.MonthlyWeights;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PersonalPriceIndexCalculatorTest {

  /** 모든 카테고리 같은 변화율. */
  private static Map<ExpenseCategoryEnum, BigDecimal> flat(String rate) {
    Map<ExpenseCategoryEnum, BigDecimal> m = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum c : ExpenseCategoryEnum.values()) {
      m.put(c, new BigDecimal(rate));
    }
    return m;
  }

  /** HOUSING 에 h, FOOD 에 1-h, 나머지 0. */
  private static MonthlyWeights weights(String housing) {
    Map<ExpenseCategoryEnum, BigDecimal> w = new EnumMap<>(ExpenseCategoryEnum.class);
    Map<ExpenseCategoryEnum, WeightStatusEnum> s = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum c : ExpenseCategoryEnum.values()) {
      w.put(c, BigDecimal.ZERO);
      s.put(c, WeightStatusEnum.USER_DEFINED);
    }
    w.put(HOUSING, new BigDecimal(housing));
    w.put(FOOD, BigDecimal.ONE.subtract(new BigDecimal(housing)));
    return new MonthlyWeights(w, s);
  }

  @Test
  @DisplayName("같은 변화율 1% 가 두 달 이어지면 1.01² 이다")
  void compoundsMonthlyRates() {
    Map<String, Map<ExpenseCategoryEnum, BigDecimal>> rates =
        Map.of("202602", flat("0.01"), "202603", flat("0.01"));
    Map<String, MonthlyWeights> w = Map.of("202602", weights("0.5"), "202603", weights("0.5"));

    BigDecimal index =
        PersonalPriceIndexCalculator.accumulate("202601", "202603", rates, w).orElseThrow();

    assertThat(index).isEqualByComparingTo("1.0201");
  }

  @Test
  @DisplayName("그 달 가중치로 카테고리 변화율을 가중한다")
  void weightsCategoryRates() {
    Map<ExpenseCategoryEnum, BigDecimal> r = flat("0");
    r.put(HOUSING, new BigDecimal("0.10"));
    Map<String, MonthlyWeights> w = Map.of("202602", weights("0.3"));

    BigDecimal index =
        PersonalPriceIndexCalculator.accumulate("202601", "202602", Map.of("202602", r), w)
            .orElseThrow();

    assertThat(index).isEqualByComparingTo("1.03");
  }

  @Test
  @DisplayName("달마다 다른 가중치면 각 달 자기 가중치로 가중해 곱한다")
  void differentWeightsPerMonth() {
    Map<ExpenseCategoryEnum, BigDecimal> r = flat("0");
    r.put(HOUSING, new BigDecimal("0.10"));
    Map<String, MonthlyWeights> w = Map.of("202602", weights("0.3"), "202603", weights("0.5"));

    BigDecimal index =
        PersonalPriceIndexCalculator.accumulate(
                "202601", "202603", Map.of("202602", r, "202603", r), w)
            .orElseThrow();

    // 1.03 × 1.05
    assertThat(index).isEqualByComparingTo("1.0815");
  }

  @Test
  @DisplayName("물가가 내리면 1 보다 작다")
  void deflation() {
    BigDecimal index =
        PersonalPriceIndexCalculator.accumulate(
                "202601",
                "202602",
                Map.of("202602", flat("-0.02")),
                Map.of("202602", weights("0.5")))
            .orElseThrow();

    assertThat(index).isEqualByComparingTo("0.98");
  }

  @Test
  @DisplayName("구간이 비면(end <= base) 1 이다")
  void emptyRange_returnsOne() {
    assertThat(PersonalPriceIndexCalculator.accumulate("202609", "202609", Map.of(), Map.of()))
        .contains(BigDecimal.ONE);
    assertThat(PersonalPriceIndexCalculator.accumulate("202610", "202609", Map.of(), Map.of()))
        .contains(BigDecimal.ONE);
  }

  @Test
  @DisplayName("구간 안에 변화율이 없는 달이나 카테고리가 빠진 달이 있으면 계산하지 않는다")
  void missingMonthOrCategory_empty() {
    Map<String, MonthlyWeights> w = Map.of("202602", weights("0.5"), "202603", weights("0.5"));
    assertThat(
            PersonalPriceIndexCalculator.accumulate(
                "202601", "202603", Map.of("202602", flat("0.01")), w))
        .isEmpty();

    Map<ExpenseCategoryEnum, BigDecimal> partial = new HashMap<>(flat("0.01"));
    partial.remove(HOUSING);
    assertThat(
            PersonalPriceIndexCalculator.accumulate(
                "202601", "202602", Map.of("202602", partial), w))
        .isEmpty();
  }
}
