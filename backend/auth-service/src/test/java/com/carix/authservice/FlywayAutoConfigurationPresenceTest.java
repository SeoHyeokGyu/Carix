package com.carix.authservice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * Flyway 를 구동할 Boot 자동설정이 클래스패스에 있는지 본다.
 *
 * <p>Boot 3.x 는 자동설정을 통짜 `spring-boot-autoconfigure` 에 담았고 `org.flywaydb:flyway-core` 만 선언해도
 * 마이그레이션이 돌았다. Boot 4 는 자동설정을 기술별 모듈로 분리해서, 라이브러리만 선언하면 클래스는 로드되지만 아무도 호출하지 않는다 — 예외도 경고도 없이
 * 마이그레이션을 건너뛴다.
 *
 * <p>실제로 물렸다. 운영 DB(`carix`)에 `auth` 스키마도 `flyway_schema_history` 도 만들어진 적이 없어
 * `users`·`invitation`·`magic_link_token`·`session` 이 존재하지 않았다. `ddl-auto: none` 이라 기동은 정상이었고 실제 인증
 * 요청에서야 드러난다.
 */
class FlywayAutoConfigurationPresenceTest {

  private static final String FLYWAY_AUTO_CONFIGURATION =
      "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration";

  @Test
  void bootFlywayAutoConfigurationIsOnTheClasspath() {
    assertDoesNotThrow(
        () -> Class.forName(FLYWAY_AUTO_CONFIGURATION),
        "spring-boot-flyway 모듈이 없다. flyway-core 만으로는 마이그레이션이 조용히 건너뛰어진다 — "
            + "spring-boot-starter-flyway 를 선언한다");
  }
}
