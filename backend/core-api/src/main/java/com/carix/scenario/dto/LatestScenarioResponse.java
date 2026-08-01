package com.carix.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "최근 시나리오 상세 응답")
public record LatestScenarioResponse(
        @Schema(description = "시나리오 고유 ID") UUID id,
        @Schema(description = "시나리오 이름") String scenarioName,
        SimulationRequest inputs,
        SimulationResponse result,
        Instant calculatedAt
) {}
