package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "가상 지수 예측 결과 응답")
public record IndexPredictionResponse(
    @Schema(description = "현재 구매력 지수", example = "94.2") double currentPurchasingPowerIndex,
    @Schema(description = "예측 구매력 지수", example = "102.8") double predictedPurchasingPowerIndex,
    @Schema(description = "지수 변동차 (포인트)", example = "8.6") double indexDifference,
    @Schema(description = "변동 상태 (INCREASED, DECREASED, STABLE)", example = "INCREASED")
        String changeStatus,
    @Schema(description = "상세 분석 텍스트 설명", example = "연봉이 5.0% 인상되고 월 지출을 500,000원 절감하면...")
        String rationale) {}
