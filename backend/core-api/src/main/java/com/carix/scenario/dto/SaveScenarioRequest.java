package com.carix.scenario.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "시나리오 저장 요청. 서버가 입력을 다시 평가해 결과와 함께 저장한다")
public record SaveScenarioRequest(
    @Size(max = 100) @Schema(description = "시나리오 이름 (선택)", example = "A사 오퍼 제안", nullable = true)
        String name,
    @NotNull @Valid EvaluateRequest request) {}
