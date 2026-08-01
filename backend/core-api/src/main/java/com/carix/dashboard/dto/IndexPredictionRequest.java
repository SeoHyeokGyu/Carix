package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "가상 지수 예측 요청")
public record IndexPredictionRequest(
        @NotNull @Schema(description = "가상 예상 연봉 인상률 (%)", example = "5.0") Double futureExpectedRaiseRate,
        @NotNull @Schema(description = "가상 월 지출 변동액 (원, 마이너스는 절감)", example = "-500000") Long futureMonthlyExpenseChange
) {}
