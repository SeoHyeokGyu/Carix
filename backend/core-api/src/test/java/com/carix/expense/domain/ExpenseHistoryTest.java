package com.carix.expense.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
            .build();

    history.replaceItems(List.of(item1));
    assertThat(history.getItems()).hasSize(1);
    assertThat(item1.getExpenseHistory()).isEqualTo(history);

    // null 전달 시 clear
    history.replaceItems(null);
    assertThat(history.getItems()).isEmpty();
  }

  @Test
  @DisplayName("replaceItems 호출 시 기존 카테고리 항목은 in-place로 갱신되어 객체 인스턴스를 유지한다")
  void testReplaceItemsInPlaceUpdate() {
    UUID userId = UUID.randomUUID();
    ExpenseHistory history = new ExpenseHistory(userId, "202609");

    ExpenseItemEntity initialFood = new ExpenseItemEntity(ExpenseCategoryEnum.FOOD, 300000L);
    ExpenseItemEntity initialHousing = new ExpenseItemEntity(ExpenseCategoryEnum.HOUSING, 500000L);

    history.replaceItems(List.of(initialFood, initialHousing));
    assertThat(history.getItems()).hasSize(2);

    ExpenseItemEntity originalFoodEntity =
        history.getItems().stream()
            .filter(e -> e.getCategory() == ExpenseCategoryEnum.FOOD)
            .findFirst()
            .orElseThrow();

    // 두 번째 온보딩 저장 시뮬레이션: FOOD 금액 변경, HOUSING 유지, TRANSPORT 신규 추가
    ExpenseItemEntity updatedFood = new ExpenseItemEntity(ExpenseCategoryEnum.FOOD, 450000L);
    ExpenseItemEntity updatedHousing = new ExpenseItemEntity(ExpenseCategoryEnum.HOUSING, 500000L);
    ExpenseItemEntity newTransport = new ExpenseItemEntity(ExpenseCategoryEnum.TRANSPORT, 100000L);

    history.replaceItems(List.of(updatedFood, updatedHousing, newTransport));

    assertThat(history.getItems()).hasSize(3);

    // 기존 Food 엔티티 인스턴스가 그대로 유지되면서 값만 갱신되었는지 검증 (새 INSERT 방지)
    ExpenseItemEntity currentFoodEntity =
        history.getItems().stream()
            .filter(e -> e.getCategory() == ExpenseCategoryEnum.FOOD)
            .findFirst()
            .orElseThrow();

    assertThat(currentFoodEntity).isSameAs(originalFoodEntity);
    assertThat(currentFoodEntity.getAmount()).isEqualTo(450000L);
  }

  @Test
  @DisplayName("replaceItems 호출 시 요청에 없는 카테고리 행은 제거된다 (입력한 것만 존재)")
  void testReplaceItemsRemovesCategoriesNotInRequest() {
    ExpenseHistory history = new ExpenseHistory(UUID.randomUUID(), "202609");
    history.replaceItems(
        List.of(
            new ExpenseItemEntity(ExpenseCategoryEnum.FOOD, 300000L),
            new ExpenseItemEntity(ExpenseCategoryEnum.HOUSING, 500000L)));

    history.replaceItems(List.of(new ExpenseItemEntity(ExpenseCategoryEnum.FOOD, 350000L)));

    assertThat(history.getItems())
        .extracting(ExpenseItemEntity::getCategory, ExpenseItemEntity::getAmount)
        .containsExactly(tuple(ExpenseCategoryEnum.FOOD, 350000L));
  }
}
