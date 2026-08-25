package com.carix.pipeline.dto;

import java.util.List;

public record CrawlRequest(
    String portal,
    String query,
    Integer maxPages,
    List<String> targetPortals,
    String outputDir,
    String format) {}
