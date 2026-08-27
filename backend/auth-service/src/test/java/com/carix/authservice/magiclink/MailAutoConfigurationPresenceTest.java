package com.carix.authservice.magiclink;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

/**
 * 메일 발송 자동설정이 클래스패스에 있는지 본다. Flyway 때와 같은 함정이다.
 *
 * <p>Boot 4 는 자동설정을 기술별 모듈로 쪼갰다. {@code jakarta.mail-api} 나 {@code angus-mail} 만 선언하면 클래스는 로드되지만
 * 아무도 {@code JavaMailSender} 를 만들지 않는다 — 예외도 경고도 없이 스텁 발송으로 떨어진다. {@code spring-boot-starter-mail}
 * 을 선언해야 한다.
 */
class MailAutoConfigurationPresenceTest {

  private static final String MAIL_AUTO_CONFIGURATION =
      "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration";

  @Test
  void bootMailAutoConfigurationIsOnTheClasspath() {
    assertDoesNotThrow(
        () -> Class.forName(MAIL_AUTO_CONFIGURATION),
        "spring-boot-mail 모듈이 없다. jakarta.mail 만으로는 JavaMailSender 가 만들어지지 않는다 — "
            + "spring-boot-starter-mail 을 선언한다");
  }
}
