package com.carix.index.cpi;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.index.dto.TopPriceDriver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/** 기여도 1위 선택. 순수 함수다. */
final class TopPriceDriverCalculator {

  static final String LABEL_SUFFIX = " 부담률 최대 상승";
  private static final int CONTRIBUTION_SCALE = 6;

  /** 카테고리 가중치와 그 가중치가 기본값으로 채워졌는지. */
  record WeightedCategory(BigDecimal weight, boolean estimated) {}

  private TopPriceDriverCalculator() {}

  /**
   * 여섯 카테고리 모두 가중치와 전년비가 있어야 고른다 — 일부만으로 고르면 "임의 품목을 고르지 않는다"(US-D1)를 어긴다. 1위 기여도가 0 이하면 물가 하락이라
   * 뱃지를 만들지 않는다(§4.3). 동률은 enum 선언 순서.
   */
  static Optional<TopPriceDriver> pick(
      Map<ExpenseCategoryEnum, WeightedCategory> weights,
      CategoryCpiRates rates,
      Map<ExpenseCategoryEnum, String> labels) {
    ExpenseCategoryEnum top = null;
    BigDecimal best = null;
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      WeightedCategory weighted = weights.get(category);
      BigDecimal rate = rates.rates().get(category);
      if (weighted == null || rate == null) {
        return Optional.empty();
      }
      BigDecimal contribution = weighted.weight().multiply(rate);
      if (best == null || contribution.compareTo(best) > 0) {
        best = contribution;
        top = category;
      }
    }
    if (best == null || best.signum() <= 0) {
      return Optional.empty();
    }
    return Optional.of(
        new TopPriceDriver(
            top.name(),
            labels.get(top) + LABEL_SUFFIX,
            weights.get(top).estimated(),
            rates.cpiMonth(),
            best.setScale(CONTRIBUTION_SCALE, RoundingMode.HALF_UP)));
  }
}
