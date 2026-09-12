package com.carix.market;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketAggregateRepository extends JpaRepository<MarketAggregate, UUID> {

  List<MarketAggregate> findByBaseYearMonth(String baseYearMonth);

  @Modifying
  @Query("DELETE FROM MarketAggregate m WHERE m.baseYearMonth = :baseYearMonth")
  void deleteByBaseYearMonth(@Param("baseYearMonth") String baseYearMonth);

  @Modifying
  @Query(
      """
      UPDATE MarketAggregate m
      SET m.salaryMin = CASE WHEN m.salaryMin IS NOT NULL AND m.salaryMin > 0 AND m.salaryMin < 1000000 THEN m.salaryMin * 10000 ELSE m.salaryMin END,
          m.salaryP25 = CASE WHEN m.salaryP25 IS NOT NULL AND m.salaryP25 > 0 AND m.salaryP25 < 1000000 THEN m.salaryP25 * 10000 ELSE m.salaryP25 END,
          m.salaryMedian = CASE WHEN m.salaryMedian IS NOT NULL AND m.salaryMedian > 0 AND m.salaryMedian < 1000000 THEN m.salaryMedian * 10000 ELSE m.salaryMedian END,
          m.salaryP75 = CASE WHEN m.salaryP75 IS NOT NULL AND m.salaryP75 > 0 AND m.salaryP75 < 1000000 THEN m.salaryP75 * 10000 ELSE m.salaryP75 END,
          m.salaryMax = CASE WHEN m.salaryMax IS NOT NULL AND m.salaryMax > 0 AND m.salaryMax < 1000000 THEN m.salaryMax * 10000 ELSE m.salaryMax END
      WHERE m.salaryMedian IS NOT NULL AND m.salaryMedian > 0 AND m.salaryMedian < 1000000
      """)
  int normalizeSalaryUnits();
}
