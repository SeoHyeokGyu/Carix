package com.carix.collector.dto;

public record CrawlRequest(
    String source, // "JOBKOREA", "SARAMIN", "ALL" (기본값 "ALL")
    String keyword, // 기본값 "IT개발"
    Integer pageLimit, // 기본값 10
    String outputDir // 선택 사항 (지정하지 않을 시 프로젝트 루트/verification/out)
    ) {
  public CrawlRequest {
    if (source == null || source.isBlank()) {
      source = "ALL";
    }
    if (keyword == null || keyword.isBlank()) {
      keyword = "IT개발";
    }
    if (pageLimit == null || pageLimit <= 0) {
      pageLimit = 10;
    }
  }
}
