package com.carix.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "최근 시나리오 상세 응답")
public record LatestScenarioResponse(
    SimulationRequest inputs,
    SimulationResponse result,
    Instant calculatedAt
) {

}
