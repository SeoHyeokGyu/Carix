package com.carix.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.jsoup.Connection;
import org.junit.jupiter.api.Test;

class SaraminCrawlerTest {

  @Test
  void connectionHasUnlimitedMaxBodySize() {
    Connection connection =
        SaraminCrawler.buildConnection(
            "https://www.saramin.co.kr/zf_user/search/recruit", Map.of());

    assertEquals(0, connection.request().maxBodySize());
  }
}
