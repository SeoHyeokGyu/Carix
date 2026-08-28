package com.carix.authservice.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청.
 *
 * <p>비밀번호는 길이만 본다. 대소문자·특수문자 구성을 강제하면 사용자를 예측 가능한 패턴으로 밀어넣는다 (SECURITY.md §1).
 */
public record RegisterRequest(
    @Schema(example = "user@example.com") @NotBlank @Email String email,
    @Schema(example = "최소 8자") @NotBlank @Size(min = 8) String password) {}
