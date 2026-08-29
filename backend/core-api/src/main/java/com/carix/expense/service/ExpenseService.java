package com.carix.expense.service;

import com.carix.expense.domain.*;
import com.carix.expense.dto.ExpenseHistoryResponse;
import com.carix.expense.dto.ExpenseItem;
import com.carix.expense.dto.ExpenseUpdateRequest;
import com.carix.expense.exception.InvalidExpenseCategoryException;
import com.carix.expense.repository.ExpenseHistoryRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseHistoryRepository repository;

    public ExpenseService(ExpenseHistoryRepository repository) {
        this.repository = repository;
    }

    public List<ExpenseHistoryResponse> getHistory(UUID userId) {
        return repository.findByUserIdOrderByBaseYearMonthDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ExpenseHistoryResponse upsert(UUID userId, String baseYearMonth, ExpenseUpdateRequest request) {
        Map<ExpenseCategoryEnum, Long> submitted = new LinkedHashMap<>();
        for (var item : request.items()) {
            ExpenseCategoryEnum category = parseCategory(item.category());
            submitted.put(category, item.amount());
        }

        List<ExpenseItemEntity> items = new ArrayList<>();
        for (ExpenseCategoryEnum category : ExpenseCategoryEnum.values()) {
            if (submitted.containsKey(category)) {
                items.add(new ExpenseItemEntity(category, submitted.get(category), WeightStatusEnum.USER_DEFINED));
            } else {
                items.add(new ExpenseItemEntity(category, defaultAmountFor(category), WeightStatusEnum.DEFAULT_CPI));
            }
        }

        ExpenseHistory history = repository.findByUserIdAndBaseYearMonth(userId, baseYearMonth)
                .orElseGet(() -> new ExpenseHistory(userId, baseYearMonth));
        history.replaceItems(items);
        var saved = repository.save(history);

        // TODO: C의 recalculateSnapshot(userId, fromYm) 트리거 — 계약(§4) 확정 후 연결
        return toResponse(saved);
    }

    private ExpenseCategoryEnum parseCategory(String raw) {
        try {
            return ExpenseCategoryEnum.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidExpenseCategoryException(raw);
        }
    }

    // TODO: config/default-expense-weights.yml 로 교체. 지금은 플레이스홀더.
    private Long defaultAmountFor(ExpenseCategoryEnum category) {
        return 0L;
    }

    private ExpenseHistoryResponse toResponse(ExpenseHistory history) {
        List<ExpenseItem> items = history.getItems().stream()
                .map(e -> new ExpenseItem(
                        e.getCategory().name(),
                        e.getAmount() != null ? e.getAmount() : 0L,
                        e.getWeightStatus().name()))
                .toList();
        return new ExpenseHistoryResponse(history.getBaseYearMonth(), items, computeOverallStatus(history));
    }

    private String computeOverallStatus(ExpenseHistory history) {
        Set<WeightStatusEnum> statuses = history.getItems().stream()
                .map(ExpenseItemEntity::getWeightStatus)
                .collect(java.util.stream.Collectors.toSet());
        if (statuses.size() == 1) {
            return statuses.iterator().next().name();
        }
        return "PARTIALLY_ESTIMATED";
    }
}