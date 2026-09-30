package com.carix.index.cpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.dto.AppliedExpenseResponse;
import com.carix.expense.service.AppliedExpenseService;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultTopPriceDriverProviderTest {

  private static final UUID USER = UUID.randomUUID();

  private AppliedExpenseService expenseService;
  private CategoryCpiRateService rateService;
  private CpiCategoryConfig config;
  private DefaultTopPriceDriverProvider provider;

  @BeforeEach
  void setUp() {
    expenseService = mock(AppliedExpenseService.class);
    rateService = mock(CategoryCpiRateService.class);
    config = mock(CpiCategoryConfig.class);
    Map<ExpenseCategoryEnum, String> labels = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      labels.put(category, category.name());
    }
    when(config.labels()).thenReturn(labels);
    provider = new DefaultTopPriceDriverProvider(expenseService, rateService, config);
  }

  /** TRANSPORT 전년비만 크고 나머지는 1%. */
  private static CategoryCpiRates transportLeads() {
    Map<ExpenseCategoryEnum, BigDecimal> rates = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      rates.put(
          category, new BigDecimal(category == ExpenseCategoryEnum.TRANSPORT ? "0.05" : "0.01"));
    }
    return new CategoryCpiRates("202609", rates);
  }

  private static AppliedExpenseResponse applied(String amountBasis, Long amount, String status) {
    List<AppliedExpenseResponse.Category> categories =
        List.of(ExpenseCategoryEnum.values()).stream()
            .map(
                c ->
                    new AppliedExpenseResponse.Category(
                        c.name(), amount, new BigDecimal("0.1667"), status))
            .toList();
    return new AppliedExpenseResponse("202609", 1, "2025", amountBasis, categories);
  }

  @Test
  @DisplayName("금액 근거가 있으면 금액 비중으로 가중한다")
  void amountBasis_usesAmountShares() {
    when(rateService.yearOverYear("202609")).thenReturn(Optional.of(transportLeads()));
    when(expenseService.resolve(USER, "202609"))
        .thenReturn(applied("USER_INPUT", 100_000L, "USER_DEFINED"));

    var driver = provider.find(USER, "202609").orElseThrow();

    assertThat(driver.category()).isEqualTo("TRANSPORT");
    assertThat(driver.estimated()).isFalse();
  }

  @Test
  @DisplayName("소비를 입력한 적이 없으면(NONE) 기본 가중치로 계산하고 추정으로 표시한다")
  void noAmountBasis_usesDefaultWeightsAndEstimated() {
    when(rateService.yearOverYear("202609")).thenReturn(Optional.of(transportLeads()));
    when(expenseService.resolve(USER, "202609")).thenReturn(applied("NONE", null, "DEFAULT_CPI"));

    var driver = provider.find(USER, "202609").orElseThrow();

    assertThat(driver.category()).isEqualTo("TRANSPORT");
    assertThat(driver.estimated()).isTrue();
  }

  @Test
  @DisplayName("CPI 전년비를 못 구하면 빈 값")
  void noRates_empty() {
    when(rateService.yearOverYear("202609")).thenReturn(Optional.empty());

    assertThat(provider.find(USER, "202609")).isEmpty();
  }

  @Test
  @DisplayName("계산 중 예외가 나도 던지지 않고 빈 값 — 스냅샷 저장을 막지 않는다")
  void exception_empty() {
    when(rateService.yearOverYear("202609")).thenThrow(new IllegalStateException("DB 오류"));

    assertThat(provider.find(USER, "202609")).isEmpty();
  }
}
