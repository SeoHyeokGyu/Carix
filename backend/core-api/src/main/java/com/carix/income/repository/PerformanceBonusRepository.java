package com.carix.income.repository;

import com.carix.income.domain.PerformanceBonus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerformanceBonusRepository extends JpaRepository<PerformanceBonus, UUID> {
  List<PerformanceBonus> findByUserIdOrderByPaidMonthDesc(UUID userId);

  Optional<PerformanceBonus> findByIdAndUserId(UUID id, UUID userId);
}
