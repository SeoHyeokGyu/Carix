package com.carix.crawler;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CrawlerTest {

  @Test
  @Disabled(
      """
        실제 잡코리아·사람인을 10페이지씩 크롤링하는 통합 실행이다. `./gradlew test` 에 섞여
        돌면 (1) 외부 사이트에 부하를 주고 (2) 10분 이상 걸리며 (3) verification/out 의
        CSV·원문 스냅샷을 오늘 자 크롤 결과로 덮어써 관측 시점을 망가뜨린다.
        수집이 필요하면 CarixCrawlerApp 을 직접 실행한다.""")
  public void runJobPortalCrawlerApp() {
    System.out.println("[Test] Starting CarixCrawlerApp Job Category Integration Test...");
    try {
      CarixCrawlerApp.main(new String[0]);
      System.out.println("[Test] CarixCrawlerApp Integration Test Finished Successfully.");
    } catch (Exception e) {
      e.printStackTrace();
      org.junit.jupiter.api.Assertions.fail(
          "Integration test failed with exception: " + e.getMessage());
    }
  }
}
