package com.carix.wagestat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WageStatObservationRepository extends JpaRepository<WageStatObservation, UUID> {

  Optional<WageStatObservation>
      findByJobFamilyAndExperienceBracketAndRegionAndBasePeriodAndStatVintage(
          String jobFamily,
          String experienceBracket,
          String region,
          String basePeriod,
          String statVintage);

  List<WageStatObservation> findByBasePeriodOrderByJobFamilyAscExperienceBracketAsc(
      String basePeriod);
}
