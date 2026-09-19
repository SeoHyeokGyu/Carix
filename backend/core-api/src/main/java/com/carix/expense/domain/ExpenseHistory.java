package com.carix.expense.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "expense_history", schema = "core")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExpenseHistory {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "base_year_month", nullable = false, length = 6)
  private String baseYearMonth;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Builder.Default
  @OneToMany(mappedBy = "expenseHistory", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ExpenseItemEntity> items = new ArrayList<>();

  public ExpenseHistory(UUID userId, String baseYearMonth) {
    this.userId = userId;
    this.baseYearMonth = baseYearMonth;
    this.items = new ArrayList<>();
  }

  /**
   * 카테고리별 지출 항목을 안전하게 갱신합니다.
   *
   * <p>전체 삭제(clear) 후 재삽입 대신 기존 엔티티의 값을 in-place로 갱신하여, {@code (expense_history_id, category)}
   * UNIQUE 제약조건 충돌(새 엔티티 INSERT가 기존 DELETE보다 먼저 발생하는 문제)을 방지합니다.
   */
  public void replaceItems(List<ExpenseItemEntity> newItems) {
    if (this.items == null) {
      this.items = new ArrayList<>();
    }

    if (newItems == null || newItems.isEmpty()) {
      this.items.clear();
      return;
    }

    Map<ExpenseCategoryEnum, ExpenseItemEntity> existingByCategory = new HashMap<>();
    for (ExpenseItemEntity item : this.items) {
      existingByCategory.put(item.getCategory(), item);
    }

    Set<ExpenseCategoryEnum> newCategories = new HashSet<>();
    for (ExpenseItemEntity newItem : newItems) {
      ExpenseCategoryEnum category = newItem.getCategory();
      newCategories.add(category);

      ExpenseItemEntity existing = existingByCategory.get(category);
      if (existing != null) {
        existing.update(newItem.getAmount());
      } else {
        newItem.assignTo(this);
        this.items.add(newItem);
      }
    }

    this.items.removeIf(item -> !newCategories.contains(item.getCategory()));
  }
}
