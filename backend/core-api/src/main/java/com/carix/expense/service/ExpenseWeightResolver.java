package com.carix.expense.service;

import com.carix.expense.config.DefaultExpenseWeightProvider;
import com.carix.expense.domain.*;
import com.carix.expense.repository.ExpenseHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ExpenseWeightResolver {

  private final ExpenseHistoryRepository repository;
  private final DefaultExpenseWeightProvider defaultWeightProvider;

  ExpenseWeightResolver(
      ExpenseHistoryRepository repository, DefaultExpenseWeightProvider defaultWeightProvider) {
    this.repository = repository;
    this.defaultWeightProvider = defaultWeightProvider;
  }

  private static final int WEIGHT_SCALE = 10;

  record ResolvedCategory(ExpenseCategoryEnum category, long amount, WeightStatusEnum status) {}

  /**
   * 여러 달의 가중치를 한 번의 조회로 구한다. 기록 행이 없는 달은 행은 있는데 항목이 빈 달과 같다 — 앞선 완전 입력 달을 상속하고(§1.1 2단계), 그마저 없으면
   * 기본 가중치다(3단계).
   */
  public Map<String, MonthlyWeights> resolveMonths(UUID userId, List<String> yearMonths) {
    List<ExpenseHistory> historiesDesc = repository.findByUserIdOrderByBaseYearMonthDesc(userId);
    Map<String, ExpenseHistory> byMonth = new HashMap<>();
    historiesDesc.forEach(h -> byMonth.put(h.getBaseYearMonth(), h));

    Map<String, MonthlyWeights> result = new LinkedHashMap<>();
    for (String ym : yearMonths) {
      ExpenseHistory row = byMonth.get(ym);
      List<ResolvedCategory> resolved =
          row != null ? resolve(row, historiesDesc) : propagateOrDefault(historiesDesc, ym);
      result.put(ym, toWeights(resolved));
    }
    return result;
  }

  private MonthlyWeights toWeights(List<ResolvedCategory> resolved) {
    long total = resolved.stream().mapToLong(ResolvedCategory::amount).sum();
    Map<ExpenseCategoryEnum, BigDecimal> defaults = defaultWeightProvider.getDefaultWeights();
    Map<ExpenseCategoryEnum, BigDecimal> weights = new EnumMap<>(ExpenseCategoryEnum.class);
    Map<ExpenseCategoryEnum, WeightStatusEnum> statuses = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ResolvedCategory c : resolved) {
      weights.put(
          c.category(),
          total > 0
              ? BigDecimal.valueOf(c.amount())
                  .divide(BigDecimal.valueOf(total), WEIGHT_SCALE, RoundingMode.HALF_UP)
              : defaults.get(c.category()));
      statuses.put(c.category(), c.status());
    }
    return new MonthlyWeights(weights, statuses);
  }

  /** domain-logic.md §1.1 Step 1~4 를 순서대로 적용해 6개 카테고리 전부를 채운다. */
  List<ResolvedCategory> resolve(ExpenseHistory history) {
    return resolve(history, null);
  }

  /** {@code historiesDesc} 가 {@code null} 이면 상속이 필요할 때 저장소를 읽는다. */
  private List<ResolvedCategory> resolve(
      ExpenseHistory history, List<ExpenseHistory> historiesDesc) {
    Map<ExpenseCategoryEnum, Long> entered = new EnumMap<>(ExpenseCategoryEnum.class);
    for (ExpenseItemEntity item : history.getItems()) {
      entered.put(item.getCategory(), item.getAmount());
    }
    long enteredSum = entered.values().stream().mapToLong(Long::longValue).sum();

    // Step 1 — 6개 전부 입력 + 합계 > 0
    if (entered.size() == ExpenseCategoryEnum.values().length && enteredSum > 0) {
      return entered.entrySet().stream()
          .map(e -> new ResolvedCategory(e.getKey(), e.getValue(), WeightStatusEnum.USER_DEFINED))
          .toList();
    }

    // 입력이 아예 없거나(항목 0개) 입력 합계가 0원 — Step 2/3
    if (entered.isEmpty() || enteredSum == 0) {
      return propagateOrDefault(
          historiesDesc != null
              ? historiesDesc
              : repository.findByUserIdOrderByBaseYearMonthDesc(history.getUserId()),
          history.getBaseYearMonth());
    }

    // Step 4 — 부분 입력 혼합
    return blendPartial(entered, enteredSum);
  }

  private List<ResolvedCategory> blendPartial(
      Map<ExpenseCategoryEnum, Long> entered, long enteredSum) {
    Map<ExpenseCategoryEnum, BigDecimal> defaultWeights = defaultWeightProvider.getDefaultWeights();

    BigDecimal sumOfEnteredDefaultWeights =
        entered.keySet().stream().map(defaultWeights::get).reduce(BigDecimal.ZERO, BigDecimal::add);

    List<ResolvedCategory> result = new ArrayList<>();
    for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
      if (entered.containsKey(category)) {
        result.add(
            new ResolvedCategory(category, entered.get(category), WeightStatusEnum.USER_DEFINED));
      } else {
        long interpolated =
            defaultWeights
                .get(category)
                .multiply(BigDecimal.valueOf(enteredSum))
                .divide(sumOfEnteredDefaultWeights, 0, RoundingMode.HALF_UP)
                .longValueExact();
        result.add(new ResolvedCategory(category, interpolated, WeightStatusEnum.DEFAULT_CPI));
      }
    }
    return result;
  }

  /** Step 2 — 과거 "완전 입력" 월 역탐색. Step 3 — 그마저 없으면 기본 가중치 전체 대체. */
  private List<ResolvedCategory> propagateOrDefault(
      List<ExpenseHistory> historiesDesc, String baseYearMonth) {
    Optional<ExpenseHistory> source =
        historiesDesc.stream()
            .filter(h -> h.getBaseYearMonth().compareTo(baseYearMonth) < 0)
            .filter(h -> h.getItems().size() == ExpenseCategoryEnum.values().length)
            .filter(h -> sumOf(h) > 0)
            .findFirst();

    if (source.isPresent()) {
      return source.get().getItems().stream()
          .map(
              item ->
                  new ResolvedCategory(
                      item.getCategory(), item.getAmount(), WeightStatusEnum.PROPAGATED))
          .toList();
    }

    // 절대 금액 기준이 없다 — 가중치만 의미 있고 amount 는 0으로 둔다 (PPI 엔진은 weight 만 쓴다)
    return defaultWeightProvider.getDefaultWeights().keySet().stream()
        .map(category -> new ResolvedCategory(category, 0L, WeightStatusEnum.DEFAULT_CPI))
        .toList();
  }

  private long sumOf(ExpenseHistory h) {
    return h.getItems().stream().mapToLong(ExpenseItemEntity::getAmount).sum();
  }
}
