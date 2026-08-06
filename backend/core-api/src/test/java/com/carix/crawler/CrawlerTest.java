package com.carix.crawler;

import org.junit.jupiter.api.Test;

public class CrawlerTest {

    @Test
    public void runJobPortalCrawlerApp() {
        System.out.println("[Test] Starting CarixCrawlerApp Job Category Integration Test...");
        try {
            CarixCrawlerApp.main(new String[0]);
            System.out.println("[Test] CarixCrawlerApp Integration Test Finished Successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            org.junit.jupiter.api.Assertions.fail("Integration test failed with exception: " + e.getMessage());
        }
    }
}
