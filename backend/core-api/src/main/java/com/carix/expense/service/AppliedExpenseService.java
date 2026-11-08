package com.carix.expense.service;

import com.carix.expense.config.DefaultExpenseWeightProvider;
import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.ExpenseHistory;
import com.carix.expense.domain.WeightStatusEnum;
import com.carix.expense.dto.AppliedExpenseResponse;
import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.snapshot.YearMonths;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [Member C 소유] 그 달 지수 계산에 실제로 적용된 소비 금액·가중치를 읽는다 (`US-O6`, `S-09` 검토 화면).
 *
 * <p>소비 도메인은 Member A 소관이다. 이 클래스는 A 의 로직을 바꾸지 않고 {@link ExpenseWeightResolver} 의 결과를 읽어 화면 계약으로
 * 옮기기만 한다 — 같은 패키지에 두는 이유는 리졸버가 package-private 이기 때문이다.
 *
 * <p><b>`NONE` 판정은 추론이 아니다.</b> 리졸버의 Step 4(부분 입력 보간)는 입력 합계 &gt; 0 을 전제하므로 보간 금액은 항상 양수다. Step 1 은
 * 전부 {@code USER_DEFINED}, Step 2 는 전부 {@code PROPAGATED} 다. 따라서 "여섯 개 전부 {@code DEFAULT_CPI} 이면서
 * 합계 0" 은 Step 3(절대 금액 기준 없음) 하나뿐이다.
 */
@Service
public class AppliedExpenseService {

  private final ExpenseHistoryRepository repository;
  private final ExpenseWeightResolver resolver;
  private final DefaultExpenseWeightProvider weightProvider;

  AppliedExpenseService(
      ExpenseHistoryRepository repository,
      ExpenseWeightResolver resolver,
      DefaultExpenseWeightProvider weightProvider) {
    this.repository = repository;
    this.resolver = resolver;
    this.weightProvider = weightProvider;
  }

  /**
   * @param baseYearMonth 기준 년월. {@code null} 이면 최신 소비 이력 월, 그마저 없으면 현재월
   */
  @Transactional(readOnly = true)
  public AppliedExpenseResponse resolve(UUID userId, String baseYearMonth) {
    String targetYearMonth = resolveTargetYearMonth(userId, baseYearMonth);

    // 행이 없어도 빈 이력으로 리졸버를 태운다 — Step 2(전파)·Step 3(기본 가중치)로 자연히 떨어진다.
    ExpenseHistory history =
        repository
            .findByUserIdAndBaseYearMonth(userId, targetYearMonth)
            .orElseGet(() -> new ExpenseHistory(userId, targetYearMonth));

    List<ExpenseWeightResolver.ResolvedCategory> resolved = resolver.resolve(history);
    boolean hasAmountBasis = hasAmountBasis(resolved);
    // 금액과 같은 소비 구조를 말해야 한다 — 기본 가중치를 실으면 입력 금액과 가중치가 서로 다른 구조가 된다.
    Map<ExpenseCategoryEnum, BigDecimal> weights = resolver.toWeights(resolved).weights();

    List<AppliedExpenseResponse.Category> categories =
        resolved.stream()
            .map(
                item ->
                    new AppliedExpenseResponse.Category(
                        item.category().name(),
                        hasAmountBasis ? item.amount() : null,
                        weights.get(item.category()),
                        item.status().name()))
            .toList();

    return new AppliedExpenseResponse(
        targetYearMonth,
        weightProvider.getVersion(),
        weightProvider.getBasePeriod(),
        amountBasis(resolved, hasAmountBasis),
        categories);
  }

  private String resolveTargetYearMonth(UUID userId, String baseYearMonth) {
    if (baseYearMonth != null && !baseYearMonth.isBlank()) {
      return baseYearMonth;
    }
    return repository.findByUserIdOrderByBaseYearMonthDesc(userId).stream()
        .map(ExpenseHistory::getBaseYearMonth)
        .findFirst()
        .orElseGet(YearMonths::current);
  }

  /** 금액이 뜻을 갖는지. 순수 함수다. */
  private static boolean hasAmountBasis(List<ExpenseWeightResolver.ResolvedCategory> resolved) {
    long total = resolved.stream().mapToLong(ExpenseWeightResolver.ResolvedCategory::amount).sum();
    return total > 0;
  }

  /** 금액 출처. 순수 함수다. */
  private static String amountBasis(
      List<ExpenseWeightResolver.ResolvedCategory> resolved, boolean hasAmountBasis) {
    if (!hasAmountBasis) {
      return "NONE";
    }
    boolean allPropagated =
        resolved.stream().allMatch(item -> item.status() == WeightStatusEnum.PROPAGATED);
    return allPropagated ? "PROPAGATED" : "USER_INPUT";
  }
}
