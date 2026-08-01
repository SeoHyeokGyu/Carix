package com.carix.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "온보딩 상태 갱신 요청")
public record OnboardingStatusRequest(
    @NotBlank @Schema(example = "COMPLETED") String currentStep
) {

}
