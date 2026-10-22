package com.carix.scenario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 엔진 2 행의 {@code payload_encrypted} 평문. 초안은 {@code result} 가 {@code null} 이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioPayload(EvaluateRequest request, EvaluationResponse result) {}
