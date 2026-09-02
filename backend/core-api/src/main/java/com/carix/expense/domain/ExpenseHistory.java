package com.carix.expense.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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

  public void replaceItems(List<ExpenseItemEntity> newItems) {
    if (this.items == null) {
      this.items = new ArrayList<>();
    }
    this.items.clear();
    if (newItems != null) {
      newItems.forEach(item -> item.assignTo(this));
      this.items.addAll(newItems);
    }
  }
}
