package com.carix.collector.dto;

public record SeedLoadRequest(String dataDir // null 이면 프로젝트 루트/verification/out 탐색
    ) {}
