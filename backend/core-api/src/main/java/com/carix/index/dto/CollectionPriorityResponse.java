package com.carix.index.dto;

import java.util.List;

/** 채용공고 시장 데이터 수집 우선순위 산출 결과 응답 DTO (개발 계획 §2 b8b). */
public record CollectionPriorityResponse(
    String baseYearMonth,
    String source,
    int minSampleSize,
    int totalEvaluatedCombinations,
    int underSampledCount,
    List<CollectionPriorityItem> priorities) {}
