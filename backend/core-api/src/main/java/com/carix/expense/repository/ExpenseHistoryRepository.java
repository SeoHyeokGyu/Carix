package com.carix.expense.repository;

import com.carix.expense.domain.ExpenseHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseHistoryRepository extends JpaRepository<ExpenseHistory, UUID> {
  List<ExpenseHistory> findByUserIdOrderByBaseYearMonthDesc(UUID userId);

  Optional<ExpenseHistory> findByUserIdAndBaseYearMonth(UUID userId, String baseYearMonth);
}
