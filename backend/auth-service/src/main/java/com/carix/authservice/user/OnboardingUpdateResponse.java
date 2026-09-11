package com.carix.authservice.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "온보딩 단계 갱신 응답")
public record OnboardingUpdateResponse(
    @Schema(example = "INCOME") String currentStep, @Schema(example = "false") boolean completed) {

  public static OnboardingUpdateResponse from(User user) {
    return new OnboardingUpdateResponse(user.getOnboardingStep(), user.isOnboardingComplete());
  }
}
