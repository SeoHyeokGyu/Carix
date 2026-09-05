package com.carix.pipeline.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 최근 수집/정규화된 채용공고 미리보기 응답 DTO. */
public record RecentPostingResponse(
    UUID id,
    String source,
    String externalId,
    String title,
    String companyName,
    String jobFamily,
    Long salaryMin,
    Long salaryMax,
    String region,
    Integer experienceMin,
    Integer experienceMax,
    List<String> skills,
    Instant collectedAt) {}
