package com.carix.authservice.account;

import com.carix.authservice.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 가입·로그인·세션 조회가 공유하는 응답 형식 (API 규격 §2). */
@Schema(description = "인증 결과")
public record AuthResponse(
    UUID userId,
    String email,
    @Schema(example = "PROFILE", description = "온보딩 진행 단계") String onboardingStep) {

  public static AuthResponse from(User user) {
    return new AuthResponse(user.getId(), user.getEmail(), user.getOnboardingStep());
  }
}
