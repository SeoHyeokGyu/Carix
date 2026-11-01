package com.carix.snapshot.repository;

import com.carix.snapshot.domain.IndexSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndexSnapshotRepository extends JpaRepository<IndexSnapshot, UUID> {

  Optional<IndexSnapshot> findByUserIdAndBaseYearMonthAndDataVersionAndRuleVersion(
      UUID userId, String baseYearMonth, String dataVersion, String ruleVersion);

  List<IndexSnapshot> findByUserIdOrderByBaseYearMonthDesc(UUID userId);

  Optional<IndexSnapshot> findFirstByUserIdOrderByBaseYearMonthDesc(UUID userId);

  List<IndexSnapshot> findByUserIdAndBaseYearMonth(UUID userId, String baseYearMonth);

  void deleteByUserId(UUID userId);
}
