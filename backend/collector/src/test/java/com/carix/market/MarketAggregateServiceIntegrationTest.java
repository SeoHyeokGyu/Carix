package com.carix.market;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MarketAggregateServiceIntegrationTest {

  @Autowired private MarketAggregateService marketAggregateService;

  @Test
  @Disabled("원격 DB 실측 집계 실행용")
  void runRealMarketAggregation() {
    int rows = marketAggregateService.aggregateMarketDataForMonth("2026-08");
    System.out.println(">>> Generated real market aggregate rows: " + rows);
  }
}
