package com.carix.index.cpi;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.dto.AppliedExpenseResponse;
import com.carix.expense.service.AppliedExpenseService;
import com.carix.index.TopPriceDriverProvider;
import com.carix.index.cpi.TopPriceDriverCalculator.WeightedCategory;
import com.carix.index.dto.TopPriceDriver;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 물가 기여도 1위 (c12). 그 달에 <b>실제로 적용된</b> 소비 가중치를 쓴다 — 통계청 평균이 아니다. 가중치 해석은 A 소관 {@link
 * AppliedExpenseService} 를 그대로 읽는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTopPriceDriverProvider implements TopPriceDriverProvider {

  private static final String NO_AMOUNT_BASIS = "NONE";
  private static final String DEFAULT_WEIGHT_STATUS = "DEFAULT_CPI";
  private static final int SHARE_SCALE = 6;

  private final AppliedExpenseService expenseService;
  private final CategoryCpiRateService rateService;
  private final CpiCategoryConfig config;

  @Override
  public Optional<TopPriceDriver> find(UUID userId, String baseYearMonth) {
    try {
      Optional<CategoryCpiRates> rates = rateService.yearOverYear(baseYearMonth);
      if (rates.isEmpty()) {
        return Optional.empty();
      }
      Map<ExpenseCategoryEnum, WeightedCategory> weights =
          weights(expenseService.resolve(userId, baseYearMonth));
      return TopPriceDriverCalculator.pick(weights, rates.get(), config.labels());
    } catch (RuntimeException e) {
      // 뱃지 하나 때문에 지수 스냅샷이 안 써지면 안 된다. 사유는 남기고 뱃지만 뺀다.
      log.warn("[물가기여도] {} 산출 실패 — 뱃지 없이 스냅샷을 쓴다", baseYearMonth, e);
      return Optional.empty();
    }
  }

  /** 금액 근거가 있으면 금액 비중, 없으면 기본 가중치. 순수 함수다. */
  static Map<ExpenseCategoryEnum, WeightedCategory> weights(AppliedExpenseResponse applied) {
    boolean hasAmountBasis = !NO_AMOUNT_BASIS.equals(applied.amountBasis());
    long total =
        applied.categories().stream()
            .mapToLong(category -> category.amount() == null ? 0L : category.amount())
            .sum();
    boolean useAmounts = hasAmountBasis && total > 0;

    Map<ExpenseCategoryEnum, WeightedCategory> weights = new EnumMap<>(ExpenseCategoryEnum.class);
    for (AppliedExpenseResponse.Category category : applied.categories()) {
      BigDecimal weight =
          useAmounts
              ? BigDecimal.valueOf(category.amount() == null ? 0L : category.amount())
                  .divide(BigDecimal.valueOf(total), SHARE_SCALE, RoundingMode.HALF_UP)
              : category.weight();
      weights.put(
          ExpenseCategoryEnum.valueOf(category.category()),
          new WeightedCategory(weight, DEFAULT_WEIGHT_STATUS.equals(category.weightStatus())));
    }
    return weights;
  }
}
