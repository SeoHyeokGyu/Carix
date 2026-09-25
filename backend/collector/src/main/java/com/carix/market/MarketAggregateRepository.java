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
}
