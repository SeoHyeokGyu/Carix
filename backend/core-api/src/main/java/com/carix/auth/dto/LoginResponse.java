package com.carix.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "로그인 성공 응답")
public record LoginResponse(
    @Schema(description = "사용자 고유 식별자") UUID userId,
    @Schema(example = "user@example.com") String email,
    @Schema(description = "온보딩 완료 여부") boolean isOnboardingComplete
) {

}
