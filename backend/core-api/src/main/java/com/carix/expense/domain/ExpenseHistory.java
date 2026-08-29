package com.carix.expense.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "expense_history", schema = "core")
public class ExpenseHistory {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "base_year_month", nullable = false, length = 6)
    private String baseYearMonth;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "expenseHistory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ExpenseItemEntity> items = new ArrayList<>();

    protected ExpenseHistory() {}

    public ExpenseHistory(UUID userId, String baseYearMonth) {
        this.userId = userId;
        this.baseYearMonth = baseYearMonth;
    }

    public void replaceItems(List<ExpenseItemEntity> newItems) {
        items.clear();
        newItems.forEach(item -> item.assignTo(this));
        items.addAll(newItems);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getBaseYearMonth() { return baseYearMonth; }
    public List<ExpenseItemEntity> getItems() { return items; }
}