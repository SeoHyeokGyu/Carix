package com.carix.authservice.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 온보딩 단계 갱신 요청. 허용값을 여기서 막아 잘못된 단계가 users 에 들어가지 않게 한다. */
@Schema(description = "온보딩 단계 갱신 요청")
public record OnboardingUpdateRequest(
    @NotBlank
        @Pattern(regexp = "PROFILE|INCOME|EXPENSE|SKILLS|REVIEW|COMPLETED")
        @Schema(example = "INCOME")
        String currentStep) {}
