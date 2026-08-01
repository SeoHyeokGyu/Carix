package com.carix.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "시뮬레이션 모의 계산 요청")
public record SimulationRequest(
    @PositiveOrZero @Schema(description = "세전 연봉 (선택적 가상 입력)", example = "60000000", nullable = true) Long currentGrossSalary,
    @NotNull @Schema(description = "현 직장 향후 12개월 예상 연봉 인상률 (%)", example = "3.5") Double stayExpectedRaiseRate,
    @NotNull @PositiveOrZero @Schema(description = "이직 목표 세전 연봉 (원)", example = "72000000") Long moveTargetGrossSalary,
    @NotNull @PositiveOrZero @Schema(description = "이직 시 부대 비용 (원)", example = "3000000") Long moveOneTimeCost,
    @NotNull @Min(0) @Max(6) @Schema(description = "이직 구직 공백 예상 월수 (0~6)", example = "2") Integer expectedUnemploymentMonths,
    @NotNull @Schema(description = "실업급여 수급 자격 여부", example = "true") Boolean isEligibleForUnemploymentBenefit
) {

}
