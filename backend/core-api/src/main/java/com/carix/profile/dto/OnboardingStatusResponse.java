package com.carix.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 상태 응답")
public record OnboardingStatusResponse(
    @Schema(example = "EXPENSE") String currentStep,
    @Schema(description = "온보딩 완료 상태") boolean completed) {}
