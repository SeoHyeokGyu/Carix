package com.carix.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "매직링크 검증 요청")
public record VerifyRequest(
    @NotBlank
    @Email
    @Schema(description = "이메일 주소", example = "user@example.com")
    String email,

    @NotBlank
    @Schema(description = "매직링크 인증 토큰", example = "a1b2c3d4e5f6g7h8...")
    String token
) {

}
