package com.carix.collector.dto;

import java.time.Instant;

public record JobStatusResponse(
    String jobId,
    String jobType,
    String status, // RUNNING, COMPLETED, FAILED
    Instant startedAt,
    Instant finishedAt,
    Object result,
    String errorMessage) {}
