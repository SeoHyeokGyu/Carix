package com.carix.authservice.session;

import java.time.Duration;

/**
 * 세션 수명. 서버 만료와 쿠키 Max-Age 가 같은 값을 읽도록 한곳에 둔다. 어긋나면 짧은 쪽이 실제 만료가 된다.
 *
 * <p>정식 30일은 SECURITY.md §1 정책값이다. 게스트 24시간은 고정이며 연장하지 않는다. 튜닝 요구가 없어 상수로 둔다(YAGNI).
 */
public enum SessionLifetime {
  ACCOUNT(Duration.ofDays(30)),
  GUEST(Duration.ofHours(24));

  private final Duration ttl;

  SessionLifetime(Duration ttl) {
    this.ttl = ttl;
  }

  public Duration ttl() {
    return ttl;
  }
}
