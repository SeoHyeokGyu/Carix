package com.carix.pipeline.dto;

import java.time.Instant;
import java.util.Map;

public record JobStatusResponse(
    String jobId,
    String jobType,
    String status,
    Instant startedAt,
    Instant completedAt,
    Map<String, Object> details,
    String errorMessage) {}
