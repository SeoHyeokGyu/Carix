package com.carix.authservice.account;

import com.carix.authservice.user.User;

/** 가입 결과. {@code converted} 는 게스트 행을 정식 사용자로 전환했는지(새 행이 아닌지)를 뜻한다. */
public record RegistrationResult(User user, boolean converted) {}
