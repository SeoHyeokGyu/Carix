package com.carix.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "매직링크 요청")
public record MagicLinkRequest(
    @NotBlank
    @Email
    @Schema(description = "로그인 및 초대를 확인할 이메일 주소", example = "user@example.com")
    String email
) {

}
