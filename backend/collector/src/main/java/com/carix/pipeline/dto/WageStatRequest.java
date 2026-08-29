package com.carix.pipeline.dto;

/**
 * KOSIS 임금 통계 수집 및 보간 배치 실행 요청 DTO.
 *
 * @param startYear 수집 시작 연도 (예: "2020", null 또는 공백 시 최신 1개 연도만 수집)
 * @param endYear 수집 종료 연도 (예: "2024", null 또는 공백 시 startYear와 동일)
 */
public record WageStatRequest(String startYear, String endYear) {}
