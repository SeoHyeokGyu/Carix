package com.carix.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "엔진 1(이전 방식)로 저장한 시나리오의 입력")
public record SimulationRequest(
    @Schema(description = "시나리오 이름", nullable = true) String scenarioName,
    @Schema(description = "계산에 쓴 세전 연봉", example = "60000000") Long currentGrossSalary,
    @Schema(description = "현 직장 향후 12개월 예상 연봉 인상률 (%)", example = "3.5")
        Double stayExpectedRaiseRate,
    @Schema(description = "이직 목표 세전 연봉 (원)", example = "72000000") Long moveTargetGrossSalary,
    @Schema(description = "이직 시 부대 비용 (원)", example = "3000000") Long moveOneTimeCost,
    @Schema(description = "이직 구직 공백 예상 월수", example = "2") Integer expectedUnemploymentMonths,
    @Schema(description = "실업급여 수급 자격 여부", example = "true")
        Boolean isEligibleForUnemploymentBenefit) {}
