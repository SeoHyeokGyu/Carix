package com.carix.collector.dto;

import java.time.Instant;

public record JobExecutionResponse(
    String jobId, String jobType, String status, String message, Instant startedAt) {}
