package com.carix.expense.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpenseHistoryTest {

  @Test
  @DisplayName("ExpenseHistory 생성 및 replaceItems 동작 검증")
  void testReplaceItems() {
    UUID userId = UUID.randomUUID();
    ExpenseHistory history = new ExpenseHistory(userId, "202608");

    assertThat(history.getUserId()).isEqualTo(userId);
    assertThat(history.getBaseYearMonth()).isEqualTo("202608");
    assertThat(history.getItems()).isEmpty();

    ExpenseItemEntity item1 =
        ExpenseItemEntity.builder()
            .expenseHistory(history)
            .category(ExpenseCategoryEnum.FOOD)
            .amount(500000L)
            .weightStatus(WeightStatusEnum.USER_DEFINED)
            .build();

    history.replaceItems(List.of(item1));
    assertThat(history.getItems()).hasSize(1);
    assertThat(item1.getExpenseHistory()).isEqualTo(history);

    // null 전달 시 clear
    history.replaceItems(null);
    assertThat(history.getItems()).isEmpty();
  }
}
