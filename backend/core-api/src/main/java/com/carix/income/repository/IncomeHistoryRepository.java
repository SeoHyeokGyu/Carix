package com.carix.income.repository;

import com.carix.income.domain.IncomeHistory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeHistoryRepository extends JpaRepository<IncomeHistory, UUID> {
  List<IncomeHistory> findByUserIdOrderByEffectiveDateDesc(UUID userId);

  boolean existsByUserIdAndEffectiveDate(UUID userId, LocalDate effectiveDate);

  boolean existsByUserIdAndEffectiveDateAndIdNot(
      UUID userId, LocalDate effectiveDate, UUID excludeId);

  void deleteByUserId(UUID userId);
}
