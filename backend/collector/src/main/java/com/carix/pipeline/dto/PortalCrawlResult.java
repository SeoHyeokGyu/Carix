package com.carix.pipeline.dto;

import java.util.Map;

/** 포털별 크롤링 수집 실행 결과. */
public record PortalCrawlResult(
    String portal,
    String status,
    int created,
    int seenOrEnriched,
    int errors,
    String errorMessage,
    Map<String, Object> extraDetails) {

  public static PortalCrawlResult success(
      String portal, int created, int seenOrEnriched, int errors, Map<String, Object> extra) {
    return new PortalCrawlResult(portal, "SUCCESS", created, seenOrEnriched, errors, null, extra);
  }

  public static PortalCrawlResult failed(String portal, String errorMessage) {
    return new PortalCrawlResult(portal, "FAILED", 0, 0, 1, errorMessage, Map.of());
  }
}
