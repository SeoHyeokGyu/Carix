package com.carix.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "저장된 시나리오 한 건. 엔진 2 는 request·result, 엔진 1 은 legacy 만 채운다")
public record ScenarioListItem(
    UUID id,
    @Schema(description = "시나리오 이름. 이름 없이 저장했으면 null", nullable = true) String name,
    @Schema(description = "계산 방식. 1 은 이전 방식, 2 는 세후·실질 평가", example = "2") int engineVersion,
    OffsetDateTime calculatedAt,
    @Schema(description = "엔진 2 입력", nullable = true) EvaluateRequest request,
    @Schema(description = "엔진 2 저장 당시 평가 결과", nullable = true) EvaluationResponse result,
    @Schema(description = "엔진 1 저장분. 저장한 값 그대로이며 다시 계산하지 않는다", nullable = true)
        LegacyScenario legacy,
    @Schema(description = "입력 가정과 실제 소득의 비교. 저장된 가정이 빠진 행이면 null", nullable = true)
        ScenarioVerification verification) {

  public record LegacyScenario(SimulationRequest inputs, SimulationResponse result) {}
}
