package com.carix.cpi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CpiObservationRepository extends JpaRepository<CpiObservation, UUID> {

  Optional<CpiObservation> findByBaseYearMonthAndCategory(String baseYearMonth, String category);

  List<CpiObservation> findByBaseYearMonthOrderByCategoryAsc(String baseYearMonth);
}
