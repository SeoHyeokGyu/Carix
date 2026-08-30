package com.carix.pipeline.dto;

/**
 * 채용 시장 다차원 사전 집계(Market Aggregation) 실행 요청 DTO.
 *
 * <p>단일 월, 단일 연도 전체(1~12월), 연도 구간(startYear~endYear), 또는 전체 공고 기준월 일괄 집계 옵션을 제공합니다.
 *
 * @param month 특정 대상 월 (형식: "YYYYMM" 또는 "YYYY-MM", 예: "202608")
 * @param year 특정 대상 연도 전체 12개월 집계 (형식: "YYYY", 예: "2025")
 * @param startYear 연도 구간 집계 시 시작 연도 (형식: "YYYY", 예: "2020")
 * @param endYear 연도 구간 집계 시 종료 연도 (형식: "YYYY", 예: "2025")
 * @param all 전체 집계 여부 (true 시 적재된 채용공고의 모든 활동 월을 자동 추출하여 일괄 집계)
 */
public record MarketAggregateRequest(
    String month, String year, String startYear, String endYear, Boolean all) {}
