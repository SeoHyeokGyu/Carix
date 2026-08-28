package com.carix.authservice.account;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * <p>비밀번호에 길이 제약을 걸지 않는다. 정책이 바뀌어도 옛 비밀번호로 로그인할 수 있어야 하고, 여기서 걸어봤자 대조는 해시가 한다.
 */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
