package com.carix.market;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketAggregateRepository extends JpaRepository<MarketAggregate, UUID> {

  List<MarketAggregate> findByBaseYearMonth(String baseYearMonth);

  @Query("SELECT DISTINCT m.baseYearMonth FROM MarketAggregate m ORDER BY m.baseYearMonth DESC")
  List<String> findDistinctBaseYearMonths();

  @Query(
      "SELECT m FROM MarketAggregate m WHERE m.baseYearMonth = :baseYearMonth AND m.skillId IS NULL")
  List<MarketAggregate> findPeerGroupAggregatesByMonth(
      @Param("baseYearMonth") String baseYearMonth);

  @Query(
      "SELECT m FROM MarketAggregate m WHERE m.baseYearMonth = :baseYearMonth AND m.source = :source AND m.skillId IS NULL")
  List<MarketAggregate> findPeerGroupAggregatesByMonthAndSource(
      @Param("baseYearMonth") String baseYearMonth, @Param("source") String source);

  @Modifying
  @Query("DELETE FROM MarketAggregate m WHERE m.baseYearMonth = :baseYearMonth")
  void deleteByBaseYearMonth(@Param("baseYearMonth") String baseYearMonth);
}
