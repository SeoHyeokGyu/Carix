package com.carix.expense.domain;

import com.carix.common.EncryptedConverter;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "expense_item", schema = "core")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ExpenseItemEntity {

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "expense_history_id", nullable = false)
  private ExpenseHistory expenseHistory;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 50)
  private ExpenseCategoryEnum category;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "amount_encrypted", nullable = false, length = 512)
  private Long amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "weight_status", nullable = false, length = 20)
  private WeightStatusEnum weightStatus;

  public ExpenseItemEntity(
      ExpenseCategoryEnum category, Long amount, WeightStatusEnum weightStatus) {
    this.category = category;
    this.amount = amount;
    this.weightStatus = weightStatus;
  }

  public void assignTo(ExpenseHistory parent) {
    this.expenseHistory = parent;
  }

  public void update(Long amount, WeightStatusEnum weightStatus) {
    this.amount = amount;
    this.weightStatus = weightStatus;
  }
}
