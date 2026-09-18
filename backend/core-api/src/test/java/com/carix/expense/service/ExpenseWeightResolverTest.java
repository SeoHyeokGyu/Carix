package com.carix.expense.service;

import static com.carix.expense.domain.ExpenseCategoryEnum.EDUCATION;
import static com.carix.expense.domain.ExpenseCategoryEnum.FOOD;
import static com.carix.expense.domain.ExpenseCategoryEnum.HOUSING;
import static com.carix.expense.domain.ExpenseCategoryEnum.LEISURE;
import static com.carix.expense.domain.ExpenseCategoryEnum.MEDICAL;
import static com.carix.expense.domain.ExpenseCategoryEnum.TRANSPORT;
import static com.carix.expense.domain.WeightStatusEnum.DEFAULT_CPI;
import static com.carix.expense.domain.WeightStatusEnum.PROPAGATED;
import static com.carix.expense.domain.WeightStatusEnum.USER_DEFINED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.carix.expense.config.DefaultExpenseWeightProvider;
import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.domain.ExpenseItemEntity;
import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.expense.service.ExpenseWeightResolver.ResolvedCategory;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * domain-logic.md §1.1 Step 1~4 검증.
 *
 * <p>기본 가중치는 계산이 눈으로 검산되도록 HOUSING 0.30 / FOOD 0.30 / 나머지 넷 0.10 (합 1.0) 으로 고정한다.
 * DefaultExpenseWeightProvider 는 yml 로딩이 @PostConstruct 라 mock 으로 대체한다.
 */
class ExpenseWeightResolverTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String CURRENT_MONTH = "202610";

  private ExpenseHistoryRepository repository;
  private ExpenseWeightResolver resolver;

  @BeforeEach
  void setUp() {
    repository = mock(ExpenseHistoryRepository.class);
    DefaultExpenseWeightProvider weightProvider = mock(DefaultExpenseWeightProvider.class);

    Map<ExpenseCategoryEnum, BigDecimal> weights = new EnumMap<>(ExpenseCategoryEnum.class);
    weights.put(HOUSING, new BigDecimal("0.30"));
    weights.put(FOOD, new BigDecimal("0.30"));
    weights.put(TRANSPORT, new BigDecimal("0.10"));
    weights.put(MEDICAL, new BigDecimal("0.10"));
    weights.put(EDUCATION, new BigDecimal("0.10"));
    weights.put(LEISURE, new BigDecimal("0.10"));
    given(weightProvider.getDefaultWeights()).willReturn(weights);

    resolver = new ExpenseWeightResolver(repository, weightProvider);
  }

  // ---- Step 1 : 6개 전부 입력 ------------------------------------------------

  @Test
  @DisplayName("Step 1: 6개 카테고리를 모두 입력하고 합계 > 0 이면 전부 USER_DEFINED, DB 조회 없음")
  void resolve_allSixEntered_returnsUserDefined() {
    ExpenseHistory current =
        history(
            CURRENT_MONTH,
            Map.of(
                HOUSING, 700000L,
                FOOD, 500000L,
                TRANSPORT, 100000L,
                MEDICAL, 50000L,
                EDUCATION, 80000L,
                LEISURE, 120000L));

    List<ResolvedCategory> result = resolver.resolve(current);

    assertThat(result).hasSize(6).allSatisfy(r -> assertThat(r.status()).isEqualTo(USER_DEFINED));
    Map<ExpenseCategoryEnum, ResolvedCategory> by = byCategory(result);
    assertThat(by.get(HOUSING).amount()).isEqualTo(700000L);
    assertThat(by.get(LEISURE).amount()).isEqualTo(120000L);
    verifyNoInteractions(repository);
  }

  // ---- Step 4 : 부분 입력 혼합 -----------------------------------------------

  @Test
  @DisplayName("Step 4: 부분 입력이면 미입력 카테고리를 기본 가중치 비율로 보간하고 DEFAULT_CPI 로 표시")
  void resolve_partialInput_interpolatesMissingByDefaultWeightRatio() {
    // 입력 HOUSING 600000 + FOOD 300000 = 900000, 입력 카테고리 기본 가중치 합 0.60
    // 미입력 각 0.10 → 0.10 × 900000 ÷ 0.60 = 150000
    ExpenseHistory current = history(CURRENT_MONTH, Map.of(HOUSING, 600000L, FOOD, 300000L));

    List<ResolvedCategory> result = resolver.resolve(current);

    assertThat(result).hasSize(6);
    Map<ExpenseCategoryEnum, ResolvedCategory> by = byCategory(result);
    assertThat(by.get(HOUSING).amount()).isEqualTo(600000L);
    assertThat(by.get(HOUSING).status()).isEqualTo(USER_DEFINED);
    assertThat(by.get(FOOD).amount()).isEqualTo(300000L);
    assertThat(by.get(FOOD).status()).isEqualTo(USER_DEFINED);
    List.of(TRANSPORT, MEDICAL, EDUCATION, LEISURE)
        .forEach(
            category -> {
              assertThat(by.get(category).amount()).isEqualTo(150000L);
              assertThat(by.get(category).status()).isEqualTo(DEFAULT_CPI);
            });
    verifyNoInteractions(repository);
  }

  @Test
  @DisplayName("Step 4: 보간 금액은 소수점 첫째 자리에서 HALF_UP 반올림한다")
  void resolve_partialInput_roundsHalfUp() {
    // FOOD 200000 만 입력 → TRANSPORT = 0.10 × 200000 ÷ 0.30 = 66666.67 → 66667
    ExpenseHistory current = history(CURRENT_MONTH, Map.of(FOOD, 200000L));

    Map<ExpenseCategoryEnum, ResolvedCategory> by = byCategory(resolver.resolve(current));

    assertThat(by.get(HOUSING).amount()).isEqualTo(200000L);
    assertThat(by.get(TRANSPORT).amount()).isEqualTo(66667L);
  }

  // ---- Step 2 : 과거 입력월 전파 ---------------------------------------------

  @Test
  @DisplayName("Step 2: 입력이 없으면 가장 최근의 '6개 완전 입력' 과거 월을 PROPAGATED 로 전파한다")
  void resolve_noInput_propagatesFromLatestCompletePastMonth() {
    ExpenseHistory current = history(CURRENT_MONTH, Map.of());
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(
            List.of(
                completeMonth("202611", 999999L), // 미래 월 — 제외
                current, // 현재 월 — 제외
                history("202609", Map.of(FOOD, 400000L, HOUSING, 900000L)), // 부분 입력 — 제외
                completeMonth("202608", 200000L), // 가장 최근 완전 입력 월 — 선택
                completeMonth("202607", 100000L)));

    List<ResolvedCategory> result = resolver.resolve(current);

    assertThat(result).hasSize(6);
    assertThat(result)
        .allSatisfy(
            r -> {
              assertThat(r.amount()).isEqualTo(200000L);
              assertThat(r.status()).isEqualTo(PROPAGATED);
            });
  }

  @Test
  @DisplayName("Step 2: 6개를 모두 0원으로 입력한 경우도 미입력으로 보고 과거 월을 전파한다")
  void resolve_zeroSumInput_treatedAsNoInput() {
    ExpenseHistory current = completeMonth(CURRENT_MONTH, 0L);
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(current, completeMonth("202609", 300000L)));

    List<ResolvedCategory> result = resolver.resolve(current);

    assertThat(result)
        .hasSize(6)
        .allSatisfy(
            r -> {
              assertThat(r.amount()).isEqualTo(300000L);
              assertThat(r.status()).isEqualTo(PROPAGATED);
            });
  }

  // ---- Step 3 : 기본 가중치 전체 대체 ----------------------------------------

  @Test
  @DisplayName("Step 3: 입력도 전파할 과거 월도 없으면 6개 전부 DEFAULT_CPI, 금액은 0")
  void resolve_noInputAndNoPastMonth_fallsBackToDefaultCpi() {
    ExpenseHistory current = history(CURRENT_MONTH, Map.of());
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID)).willReturn(List.of(current));

    List<ResolvedCategory> result = resolver.resolve(current);

    assertThat(result)
        .extracting(ResolvedCategory::category)
        .containsExactlyInAnyOrder(ExpenseCategoryEnum.values());
    assertThat(result)
        .allSatisfy(
            r -> {
              assertThat(r.amount()).isZero();
              assertThat(r.status()).isEqualTo(DEFAULT_CPI);
            });
  }

  @Test
  @DisplayName("Step 3: 과거 월이 부분 입력뿐이면 전파하지 않고 기본 가중치로 대체한다")
  void resolve_onlyPartialPastMonths_fallsBackToDefaultCpi() {
    ExpenseHistory current = history(CURRENT_MONTH, Map.of());
    given(repository.findByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(List.of(current, history("202609", Map.of(FOOD, 400000L, HOUSING, 900000L))));

    List<ResolvedCategory> result = resolver.resolve(current);

    assertThat(result).hasSize(6).allSatisfy(r -> assertThat(r.status()).isEqualTo(DEFAULT_CPI));
  }

  // ---------------------------------------------------------------------------

  private static ExpenseHistory history(String yearMonth, Map<ExpenseCategoryEnum, Long> amounts) {
    ExpenseHistory history = new ExpenseHistory(USER_ID, yearMonth);
    history.replaceItems(
        amounts.entrySet().stream()
            .map(entry -> new ExpenseItemEntity(entry.getKey(), entry.getValue()))
            .toList());
    return history;
  }

  private static ExpenseHistory completeMonth(String yearMonth, long amountPerCategory) {
    Map<ExpenseCategoryEnum, Long> amounts = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      amounts.put(category, amountPerCategory);
    }
    return history(yearMonth, amounts);
  }

  private static Map<ExpenseCategoryEnum, ResolvedCategory> byCategory(
      List<ResolvedCategory> resolved) {
    return resolved.stream()
        .collect(Collectors.toMap(ResolvedCategory::category, Function.identity()));
  }
}
