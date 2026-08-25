package com.carix.pipeline.dto;

import java.util.Map;

public record PipelineStatsResponse(
    long totalRawIngest,
    long totalJobPostings,
    long totalSkills,
    long totalWageStats,
    Map<String, Long> postingsBySource) {}
