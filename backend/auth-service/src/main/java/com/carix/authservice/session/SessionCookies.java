package com.carix.authservice.session;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** 세션 쿠키 발급·만료. 가입·로그인·로그아웃이 같은 속성을 쓰도록 한곳에 모은다. */
public final class SessionCookies {

  public static final String NAME = "session_token";

  /** SECURITY.md §1 의 세션 유효기간과 같아야 한다. 다르면 브라우저와 서버 중 짧은 쪽이 실제 만료가 된다. */
  private static final Duration TTL = Duration.ofDays(30);

  private SessionCookies() {}

  public static void issue(HttpServletResponse response, String sessionToken) {
    response.addHeader(HttpHeaders.SET_COOKIE, build(sessionToken, TTL).toString());
  }

  public static void clear(HttpServletResponse response) {
    response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
  }

  private static ResponseCookie build(String value, Duration maxAge) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .path("/api")
        .maxAge(maxAge)
        .build();
  }
}
