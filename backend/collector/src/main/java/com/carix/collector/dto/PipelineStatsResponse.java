package com.carix.collector.dto;

import java.time.Instant;

public record PipelineStatsResponse(
    long rawIngestCount, long jobPostingCount, long skillCount, Instant queriedAt) {}
