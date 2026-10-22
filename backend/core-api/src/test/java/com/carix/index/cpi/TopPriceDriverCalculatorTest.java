package com.carix.index.cpi;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.index.cpi.TopPriceDriverCalculator.WeightedCategory;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopPriceDriverCalculatorTest {

  private static final Map<ExpenseCategoryEnum, String> LABELS = labels();

  private static Map<ExpenseCategoryEnum, String> labels() {
    Map<ExpenseCategoryEnum, String> labels = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      labels.put(category, category == ExpenseCategoryEnum.TRANSPORT ? "교통" : category.name());
    }
    return labels;
  }

  /** 여섯 카테고리 모두 같은 가중치(1/6 대신 계산 편의상 0.1)·estimated=false. */
  private static Map<ExpenseCategoryEnum, WeightedCategory> equalWeights() {
    Map<ExpenseCategoryEnum, WeightedCategory> weights = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      weights.put(category, new WeightedCategory(new BigDecimal("0.1"), false));
    }
    return weights;
  }

  private static CategoryCpiRates rates(String... categoryAndRate) {
    Map<ExpenseCategoryEnum, BigDecimal> rates = new EnumMap<>(ExpenseCategoryEnum.class);
    for (int i = 0; i < categoryAndRate.length; i += 2) {
      rates.put(
          ExpenseCategoryEnum.valueOf(categoryAndRate[i]), new BigDecimal(categoryAndRate[i + 1]));
    }
    return new CategoryCpiRates("202609", rates);
  }

  private static final String[] ALL_SMALL = {
    "HOUSING",
    "0.01",
    "FOOD",
    "0.01",
    "TRANSPORT",
    "0.05",
    "MEDICAL",
    "0.01",
    "EDUCATION",
    "0.01",
    "LEISURE",
    "0.01"
  };

  @Test
  @DisplayName("가중치 × 전년비가 가장 큰 카테고리를 고르고 라벨을 조립한다")
  void picksLargestContribution() {
    var driver =
        TopPriceDriverCalculator.pick(equalWeights(), rates(ALL_SMALL), LABELS).orElseThrow();

    assertThat(driver.category()).isEqualTo("TRANSPORT");
    assertThat(driver.label()).isEqualTo("교통 부담률 최대 상승");
    assertThat(driver.cpiMonth()).isEqualTo("202609");
    assertThat(driver.contribution()).isEqualByComparingTo("0.005");
    assertThat(driver.estimated()).isFalse();
  }

  @Test
  @DisplayName("동률이면 enum 선언 순서가 앞선 카테고리")
  void tie_prefersEnumOrder() {
    var driver =
        TopPriceDriverCalculator.pick(
                equalWeights(),
                rates(
                    "HOUSING",
                    "0.02",
                    "FOOD",
                    "0.02",
                    "TRANSPORT",
                    "0.01",
                    "MEDICAL",
                    "0.01",
                    "EDUCATION",
                    "0.01",
                    "LEISURE",
                    "0.01"),
                LABELS)
            .orElseThrow();

    assertThat(driver.category()).isEqualTo("HOUSING");
  }

  @Test
  @DisplayName("1위 기여도가 0 이하(물가 하락)면 뱃지 없음")
  void nonPositiveTop_empty() {
    var result =
        TopPriceDriverCalculator.pick(
            equalWeights(),
            rates(
                "HOUSING",
                "0",
                "FOOD",
                "-0.01",
                "TRANSPORT",
                "-0.02",
                "MEDICAL",
                "0",
                "EDUCATION",
                "-0.01",
                "LEISURE",
                "0"),
            LABELS);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("한 카테고리라도 전년비가 없으면 뱃지 없음 — 일부로 고르지 않는다")
  void missingRate_empty() {
    var result =
        TopPriceDriverCalculator.pick(
            equalWeights(),
            rates(
                "HOUSING",
                "0.01",
                "FOOD",
                "0.01",
                "TRANSPORT",
                "0.05",
                "MEDICAL",
                "0.01",
                "EDUCATION",
                "0.01"),
            LABELS);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("1위 카테고리 가중치가 기본값이면 estimated")
  void estimatedPropagates() {
    var weights = equalWeights();
    weights.put(ExpenseCategoryEnum.TRANSPORT, new WeightedCategory(new BigDecimal("0.1"), true));

    var driver = TopPriceDriverCalculator.pick(weights, rates(ALL_SMALL), LABELS).orElseThrow();

    assertThat(driver.estimated()).isTrue();
  }
}
