package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "스냅샷 재계산 결과")
public record DashboardRecalculateResponse(
    @Schema(description = "재계산된 월 수", example = "8") int recalculatedMonths,
    @Schema(description = "재계산 시작 기준월 (YYYYMM)", example = "202601") String fromYearMonth,
    @Schema(description = "갱신된 대시보드 요약 데이터") DashboardSummaryResponse summary) {}
