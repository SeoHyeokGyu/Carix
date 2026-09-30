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

  /** 차별화된 전년비: FOOD 0.02, TRANSPORT 0.05, 나머지 0.01. 이를 통해 금액 경로와 기본 가중치 경로가 다른 우승자를 낼 수 있다. */
  private static CategoryCpiRates discriminatingRates() {
    Map<ExpenseCategoryEnum, BigDecimal> rates = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      if (category == ExpenseCategoryEnum.TRANSPORT) {
        rates.put(category, new BigDecimal("0.05"));
      } else if (category == ExpenseCategoryEnum.FOOD) {
        rates.put(category, new BigDecimal("0.02"));
      } else {
        rates.put(category, new BigDecimal("0.01"));
      }
    }
    return new CategoryCpiRates("202609", rates);
  }

  /** 카테고리별 금액과 가중치를 설정한 응답. amountBasis가 "NONE"이 아니면 금액을 사용하고, "NONE"이면 가중치를 사용한다. */
  private static AppliedExpenseResponse appliedWith(
      String amountBasis,
      Map<ExpenseCategoryEnum, Long> amounts,
      Map<ExpenseCategoryEnum, BigDecimal> weights,
      String status) {
    List<AppliedExpenseResponse.Category> categories =
        List.of(ExpenseCategoryEnum.values()).stream()
            .map(
                c ->
                    new AppliedExpenseResponse.Category(
                        c.name(), amounts.get(c), weights.get(c), status))
            .toList();
    return new AppliedExpenseResponse("202609", 1, "2025", amountBasis, categories);
  }

  @Test
  @DisplayName("금액 근거가 있으면 금액 비중으로 가중한다 — FOOD가 금액 비중으로 우승")
  void amountBasis_usesAmountShares() {
    when(rateService.yearOverYear("202609")).thenReturn(Optional.of(discriminatingRates()));

    // FOOD 900_000, 나머지 20_000씩 → 합계 1_000_000
    // FOOD 비중: 0.9, 기여도: 0.9 × 0.02 = 0.018
    // TRANSPORT 비중: 0.02, 기여도: 0.02 × 0.05 = 0.001
    // FOOD가 우승 (0.018 > 0.001)
    Map<ExpenseCategoryEnum, Long> amounts = new EnumMap<>(ExpenseCategoryEnum.class);
    amounts.put(ExpenseCategoryEnum.FOOD, 900_000L);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      if (category != ExpenseCategoryEnum.FOOD) {
        amounts.put(category, 20_000L);
      }
    }

    Map<ExpenseCategoryEnum, BigDecimal> weights = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      weights.put(category, new BigDecimal("0.1667")); // 무시됨 (USER_INPUT)
    }

    when(expenseService.resolve(USER, "202609"))
        .thenReturn(appliedWith("USER_INPUT", amounts, weights, "USER_DEFINED"));

    var driver = provider.find(USER, "202609").orElseThrow();

    assertThat(driver.category()).isEqualTo("FOOD");
    assertThat(driver.contribution()).isEqualByComparingTo("0.018000");
    assertThat(driver.estimated()).isFalse();
  }

  @Test
  @DisplayName("기본 가중치를 쓸 때는 다른 우승자가 나올 수 있다 — amountBasis 체크 검증")
  void noAmountBasis_usesDefaultWeightsAndEstimated() {
    when(rateService.yearOverYear("202609")).thenReturn(Optional.of(discriminatingRates()));

    // amountBasis="NONE"이면 금액이 있어도 무시하고 가중치로 계산해야 한다. total > 0이지만
    // amountBasis 체크를 빼먹은 뮤턴트를 잡기 위해 금액을 FOOD 9대1로 설정한다.
    // 정상 코드: amountBasis != "NONE" 확인 → TRANSPORT 가중치 0.5 × 0.05 = 0.025 (우승)
    // 뮤턴트: total > 0만 확인 → FOOD 비중 0.9 × 0.02 = 0.018 (우승)
    Map<ExpenseCategoryEnum, Long> amounts = new EnumMap<>(ExpenseCategoryEnum.class);
    amounts.put(ExpenseCategoryEnum.FOOD, 900_000L);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      if (category != ExpenseCategoryEnum.FOOD) {
        amounts.put(category, 20_000L);
      }
    }

    Map<ExpenseCategoryEnum, BigDecimal> weights = new EnumMap<>(ExpenseCategoryEnum.class);
    weights.put(ExpenseCategoryEnum.TRANSPORT, new BigDecimal("0.5"));
    weights.put(ExpenseCategoryEnum.FOOD, new BigDecimal("0.15"));
    weights.put(ExpenseCategoryEnum.HOUSING, new BigDecimal("0.1"));
    weights.put(ExpenseCategoryEnum.MEDICAL, new BigDecimal("0.1"));
    weights.put(ExpenseCategoryEnum.EDUCATION, new BigDecimal("0.1"));
    weights.put(ExpenseCategoryEnum.LEISURE, new BigDecimal("0.05"));

    when(expenseService.resolve(USER, "202609"))
        .thenReturn(appliedWith("NONE", amounts, weights, "DEFAULT_CPI"));

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
  @DisplayName("소비 조회 중 예외가 나도 던지지 않고 빈 값 — 스냅샷 저장을 막지 않는다")
  void expenseResolveThrows_empty() {
    when(rateService.yearOverYear("202609")).thenReturn(Optional.of(discriminatingRates()));
    when(expenseService.resolve(USER, "202609")).thenThrow(new IllegalStateException("소비 조회 실패"));

    assertThat(provider.find(USER, "202609")).isEmpty();
  }

  @Test
  @DisplayName("계산 중 예외가 나도 던지지 않고 빈 값 — 스냅샷 저장을 막지 않는다")
  void exception_empty() {
    when(rateService.yearOverYear("202609")).thenThrow(new IllegalStateException("DB 오류"));

    assertThat(provider.find(USER, "202609")).isEmpty();
  }
}
