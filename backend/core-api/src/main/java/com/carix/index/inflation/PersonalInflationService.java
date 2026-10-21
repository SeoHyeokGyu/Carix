package com.carix.index.inflation;

import com.carix.expense.config.DefaultExpenseWeightProvider;
import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.service.ExpenseWeightResolver;
import com.carix.expense.service.MonthlyWeights;
import com.carix.index.cpi.CategoryCpiRateService;
import com.carix.index.cpi.CategoryCpiRates;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지난 12개월 개인물가 상승률을 전체 CPI(총지수) 상승률과 비교한다.
 *
 * <p>개인물가는 {@code Σ w_user(c) × yoy(c)} — 그 달 사용자 가중치로 카테고리 전년 동월 대비 변화율을 가중한다. 격차의 원인은 총지수와 6개
 * 카테고리 합성의 미세한 차이가 아니라 평균 소비구조 대비 가중치 차이의 기여({@code (w_user - w_default) × yoy})로 짚는다.
 *
 * <p>CPI 월 시차 한도 초과, 카테고리 결측, 총지수 결측이면 값을 지어내지 않고 빈 값이다.
 */
@Service
@RequiredArgsConstructor
public class PersonalInflationService {

  private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final int RATE_SCALE = 2;

  private final CategoryCpiRateService categoryCpiRateService;
  private final ExpenseWeightResolver expenseWeightResolver;
  private final DefaultExpenseWeightProvider defaultExpenseWeightProvider;

  @Transactional(readOnly = true)
  public Optional<InflationComparison> trailing12m(UUID userId, String baseYearMonth) {
    if (userId == null || baseYearMonth == null || baseYearMonth.length() != 6) {
      return Optional.empty();
    }
    Optional<String> latest = categoryCpiRateService.latestCpiMonth(baseYearMonth);
    if (latest.isEmpty()
        || YearMonth.parse(latest.get(), YM)
            .isBefore(
                YearMonth.parse(baseYearMonth, YM)
                    .minusMonths(CategoryCpiRateService.MAX_LAG_MONTHS))) {
      return Optional.empty();
    }

    Optional<CategoryCpiRates> yoy = categoryCpiRateService.yearOverYear(latest.get());
    if (yoy.isEmpty()) {
      return Optional.empty();
    }
    String cpiMonth = yoy.get().cpiMonth();
    Map<ExpenseCategoryEnum, BigDecimal> rates = yoy.get().rates();
    Optional<BigDecimal> headline = categoryCpiRateService.headlineYearOverYear(cpiMonth);
    MonthlyWeights userWeights =
        expenseWeightResolver.resolveMonths(userId, List.of(cpiMonth)).get(cpiMonth);
    if (headline.isEmpty()
        || userWeights == null
        || !hasAll(rates)
        || !hasAll(userWeights.weights())) {
      return Optional.empty();
    }

    Map<ExpenseCategoryEnum, BigDecimal> defaults =
        defaultExpenseWeightProvider.getDefaultWeights();
    BigDecimal personal = BigDecimal.ZERO;
    ExpenseCategoryEnum topCategory = null;
    BigDecimal topContribution = BigDecimal.ZERO;
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      BigDecimal weight = userWeights.weights().get(category);
      BigDecimal rate = rates.get(category);
      personal = personal.add(weight.multiply(rate));
      BigDecimal contribution = weight.subtract(defaults.get(category)).multiply(rate);
      if (topCategory == null || contribution.abs().compareTo(topContribution.abs()) > 0) {
        topCategory = category;
        topContribution = contribution;
      }
    }

    BigDecimal personalPct = percent(personal);
    BigDecimal headlinePct = percent(headline.get());
    return Optional.of(
        new InflationComparison(
            cpiMonth,
            personalPct,
            headlinePct,
            personalPct.subtract(headlinePct),
            topCategory,
            percent(topContribution)));
  }

  private static boolean hasAll(Map<ExpenseCategoryEnum, BigDecimal> byCategory) {
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      if (byCategory.get(category) == null) {
        return false;
      }
    }
    return true;
  }

  private static BigDecimal percent(BigDecimal fraction) {
    return fraction.multiply(HUNDRED).setScale(RATE_SCALE, RoundingMode.HALF_UP);
  }
}
