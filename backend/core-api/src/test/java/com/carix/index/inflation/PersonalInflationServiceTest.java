package com.carix.index.inflation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.expense.config.DefaultExpenseWeightProvider;
import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.service.ExpenseWeightResolver;
import com.carix.expense.service.MonthlyWeights;
import com.carix.index.cpi.CategoryCpiRateService;
import com.carix.index.cpi.CategoryCpiRates;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersonalInflationServiceTest {

  private static final String CPI_MONTH = "202609";

  private final UUID userId = UUID.randomUUID();

  private CategoryCpiRateService cpiRates;
  private ExpenseWeightResolver weightResolver;
  private DefaultExpenseWeightProvider defaults;
  private PersonalInflationService service;

  @BeforeEach
  void setUp() {
    cpiRates = mock(CategoryCpiRateService.class);
    weightResolver = mock(ExpenseWeightResolver.class);
    defaults = mock(DefaultExpenseWeightProvider.class);
    service = new PersonalInflationService(cpiRates, weightResolver, defaults);

    given(defaults.getDefaultWeights())
        .willReturn(weights("0.2", "0.25", "0.15", "0.1", "0.1", "0.2"));
    given(weightResolver.resolveMonths(userId, List.of(CPI_MONTH)))
        .willReturn(
            Map.of(
                CPI_MONTH,
                new MonthlyWeights(weights("0.4", "0.15", "0.15", "0.1", "0.1", "0.1"), Map.of())));
    given(cpiRates.latestCpiMonth("202609")).willReturn(Optional.of(CPI_MONTH));
    given(cpiRates.yearOverYear(CPI_MONTH))
        .willReturn(
            Optional.of(
                new CategoryCpiRates(
                    CPI_MONTH, weights("0.03", "0.02", "0.01", "0.01", "0.01", "0.02"))));
    given(cpiRates.headlineYearOverYear(CPI_MONTH))
        .willReturn(Optional.of(new BigDecimal("0.025")));
  }

  private static Map<ExpenseCategoryEnum, BigDecimal> weights(
      String housing,
      String food,
      String transport,
      String medical,
      String education,
      String leisure) {
    Map<ExpenseCategoryEnum, BigDecimal> map = new EnumMap<>(ExpenseCategoryEnum.class);
    map.put(ExpenseCategoryEnum.HOUSING, new BigDecimal(housing));
    map.put(ExpenseCategoryEnum.FOOD, new BigDecimal(food));
    map.put(ExpenseCategoryEnum.TRANSPORT, new BigDecimal(transport));
    map.put(ExpenseCategoryEnum.MEDICAL, new BigDecimal(medical));
    map.put(ExpenseCategoryEnum.EDUCATION, new BigDecimal(education));
    map.put(ExpenseCategoryEnum.LEISURE, new BigDecimal(leisure));
    return map;
  }

  @Test
  void 개인물가와_총지수_차이를_계산한다() {
    InflationComparison result = service.trailing12m(userId, "202609").orElseThrow();

    assertThat(result.cpiMonth()).isEqualTo(CPI_MONTH);
    assertThat(result.personalRate()).isEqualByComparingTo("2.05");
    assertThat(result.headlineRate()).isEqualByComparingTo("2.50");
    assertThat(result.gapPp()).isEqualByComparingTo("-0.45");
    assertThat(result.personalRate().scale()).isEqualTo(2);
  }

  @Test
  void 원인_카테고리는_가중치_차이_기여가_가장_큰_것() {
    InflationComparison result = service.trailing12m(userId, "202609").orElseThrow();

    // 주거: (0.4 - 0.2) x 3.0% = +0.60pp. 식비·여가는 -0.10 x 2.0% = -0.20pp
    assertThat(result.topGapCategory()).isEqualTo(ExpenseCategoryEnum.HOUSING);
    assertThat(result.topGapContributionPp()).isEqualByComparingTo("0.60");
  }

  @Test
  void 모든_카테고리_기여가_0이면_원인_카테고리를_비운다() {
    given(weightResolver.resolveMonths(userId, List.of(CPI_MONTH)))
        .willReturn(
            Map.of(
                CPI_MONTH,
                new MonthlyWeights(weights("0.2", "0.25", "0.15", "0.1", "0.1", "0.2"), Map.of())));

    InflationComparison result = service.trailing12m(userId, "202609").orElseThrow();

    assertThat(result.topGapCategory()).isNull();
    assertThat(result.topGapContributionPp()).isNull();
  }

  @Test
  void 총지수_없으면_empty() {
    given(cpiRates.headlineYearOverYear(CPI_MONTH)).willReturn(Optional.empty());

    assertThat(service.trailing12m(userId, "202609")).isEmpty();
  }

  @Test
  void 카테고리_하나라도_없으면_empty() {
    Map<ExpenseCategoryEnum, BigDecimal> partial =
        weights("0.03", "0.02", "0.01", "0.01", "0.01", "0.02");
    partial.remove(ExpenseCategoryEnum.EDUCATION);
    given(cpiRates.yearOverYear(CPI_MONTH))
        .willReturn(Optional.of(new CategoryCpiRates(CPI_MONTH, partial)));

    assertThat(service.trailing12m(userId, "202609")).isEmpty();
  }

  @Test
  void CPI_시차가_한도를_넘으면_empty() {
    given(cpiRates.latestCpiMonth("202612")).willReturn(Optional.of(CPI_MONTH));

    assertThat(service.trailing12m(userId, "202612")).isEmpty();
  }
}
