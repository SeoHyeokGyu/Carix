package com.carix.expense.service;

import com.carix.expense.domain.*;
import com.carix.expense.dto.ExpenseHistoryResponse;
import com.carix.expense.dto.ExpenseItem;
import com.carix.expense.dto.ExpenseUpdateRequest;
import com.carix.expense.exception.InvalidExpenseCategoryException;
import com.carix.expense.repository.ExpenseHistoryRepository;
import com.carix.snapshot.SnapshotRangeResolver;
import com.carix.snapshot.SnapshotRecalculationRequested;
import java.util.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

  private final ExpenseHistoryRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final ExpenseWeightResolver weightResolver;
  private final SnapshotRangeResolver rangeResolver;

  public ExpenseService(
      ExpenseHistoryRepository repository,
      ApplicationEventPublisher eventPublisher,
      ExpenseWeightResolver weightResolver,
      SnapshotRangeResolver rangeResolver) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.weightResolver = weightResolver;
    this.rangeResolver = rangeResolver;
  }

  public List<ExpenseHistoryResponse> getHistory(UUID userId) {
    return repository.findByUserIdOrderByBaseYearMonthDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public ExpenseHistoryResponse upsert(
      UUID userId, String baseYearMonth, ExpenseUpdateRequest request) {
    List<ExpenseItemEntity> items = new ArrayList<>();
    for (var item : request.items()) {
      ExpenseCategoryEnum category = parseCategory(item.category());
      items.add(new ExpenseItemEntity(category, item.amount()));
    }

    Optional<String> earliestBefore = weightResolver.earliestCompleteMonth(userId);
    ExpenseHistory history =
        repository
            .findByUserIdAndBaseYearMonth(userId, baseYearMonth)
            .orElseGet(() -> new ExpenseHistory(userId, baseYearMonth));
    history.replaceItems(items);
    var saved = repository.save(history);

    // 가장 이른 완전 입력이 바뀌면 그 앞 달 전부가 새 입력을 소급하므로 최초 소득월부터 다시 계산한다.
    String fromYearMonth =
        weightResolver.earliestCompleteMonth(userId).equals(earliestBefore)
            ? baseYearMonth
            : rangeResolver.earliestFrom(userId);
    eventPublisher.publishEvent(new SnapshotRecalculationRequested(userId, fromYearMonth));
    return toResponse(saved);
  }

  private ExpenseCategoryEnum parseCategory(String raw) {
    try {
      return ExpenseCategoryEnum.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new InvalidExpenseCategoryException(raw);
    }
  }

  private ExpenseHistoryResponse toResponse(ExpenseHistory history) {
    List<ExpenseWeightResolver.ResolvedCategory> resolved = weightResolver.resolve(history);

    List<ExpenseItem> items =
        resolved.stream()
            .map(r -> new ExpenseItem(r.category().name(), r.amount(), r.status().name()))
            .toList();

    Set<WeightStatusEnum> statuses =
        resolved.stream()
            .map(ExpenseWeightResolver.ResolvedCategory::status)
            .collect(java.util.stream.Collectors.toSet());
    String overallStatus =
        statuses.size() == 1 ? statuses.iterator().next().name() : "PARTIALLY_ESTIMATED";

    return new ExpenseHistoryResponse(history.getBaseYearMonth(), items, overallStatus);
  }
}
