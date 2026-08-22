package com.carix.crawler;

import java.time.Instant;

/** 원천 응답 원문 보존 레코드. 출처, 원문 ID, 원문, 수집 시각을 담는다. */
public record RawIngest(String source, String rawId, String rawContent, Instant collectedAt) {}
