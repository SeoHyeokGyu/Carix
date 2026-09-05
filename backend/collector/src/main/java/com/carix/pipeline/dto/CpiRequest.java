package com.carix.pipeline.dto;

/**
 * KOSIS 소비자물가지수 수집 배치 실행 요청 DTO.
 *
 * @param startYearMonth 수집 시작 연월 (예: "202401", null/공백 시 최신 1개월만 수집)
 * @param endYearMonth 수집 종료 연월 (예: "202412", null/공백 시 startYearMonth와 동일)
 */
public record CpiRequest(String startYearMonth, String endYearMonth) {}
