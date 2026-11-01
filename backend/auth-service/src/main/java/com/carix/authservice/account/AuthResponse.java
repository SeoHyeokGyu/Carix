package com.carix.authservice.account;

import com.carix.authservice.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** 가입·로그인·세션 조회가 공유하는 응답 형식 (API 규격 §2). */
@Schema(description = "인증 결과")
public record AuthResponse(
    UUID userId,
    String email,
    @Schema(example = "PROFILE", description = "온보딩 진행 단계") String onboardingStep,
    @Schema(description = "게스트 여부. 게스트면 email 은 null") boolean guest) {

  public static AuthResponse from(User user) {
    return new AuthResponse(
        user.getId(), user.getEmail(), user.getOnboardingStep(), user.isGuest());
  }
}
