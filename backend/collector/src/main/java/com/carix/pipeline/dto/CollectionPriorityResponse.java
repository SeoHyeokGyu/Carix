package com.carix.pipeline.dto;

import java.util.List;

/**
 * 채용공고 시장 데이터 수집 우선순위 산출 결과 응답 DTO.
 *
 * @param baseYearMonth 기준 년월 (YYYYMM)
 * @param source 평가 대상 원천 플랫폼 (ALL, JOBKOREA, SARAMIN)
 * @param minSampleSize 적용된 최소 기준 표본수
 * @param totalEvaluatedCombinations 평가된 전체 차원 조합 수
 * @param underSampledCount 표본 미달(N &lt; minSampleSize) 차원 조합 수
 * @param priorities 우선순위 정렬된 수집 대상 항목 목록
 */
public record CollectionPriorityResponse(
    String baseYearMonth,
    String source,
    int minSampleSize,
    int totalEvaluatedCombinations,
    int underSampledCount,
    List<CollectionPriorityItem> priorities) {}
