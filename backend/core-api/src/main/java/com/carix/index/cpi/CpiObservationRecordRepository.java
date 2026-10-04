package com.carix.index.cpi;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CpiObservationRecordRepository extends JpaRepository<CpiObservationRecord, UUID> {

  /** {@code YYYYMM} 은 사전순이 시간순이다. */
  Optional<CpiObservationRecord> findFirstByBaseYearMonthLessThanEqualOrderByBaseYearMonthDesc(
      String baseYearMonth);

  List<CpiObservationRecord> findByBaseYearMonthIn(Collection<String> baseYearMonths);

  List<CpiObservationRecord> findByBaseYearMonthBetween(String from, String to);
}
