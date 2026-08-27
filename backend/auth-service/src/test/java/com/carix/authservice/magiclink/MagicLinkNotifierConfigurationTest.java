package com.carix.authservice.magiclink;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MagicLinkNotifierConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  MailSenderAutoConfiguration.class, MagicLinkNotifierConfiguration.class));

  @Test
  void 메일_호스트가_없으면_로그_스텁을_쓴다() {
    runner
        .withPropertyValues(
            "carix.auth.mail.base-url=https://carix.example.com",
            "carix.auth.mail.from=beta@example.com")
        .run(
            context ->
                assertThat(context.getBean(MagicLinkNotifier.class))
                    .isInstanceOf(LoggingMagicLinkNotifier.class));
  }

  @Test
  void 메일_호스트가_있어도_base_url_이_비면_로그_스텁을_쓴다() {
    runner
        .withPropertyValues(
            "spring.mail.host=smtp.gmail.com", "carix.auth.mail.from=beta@example.com")
        .run(
            context ->
                assertThat(context.getBean(MagicLinkNotifier.class))
                    .isInstanceOf(LoggingMagicLinkNotifier.class));
  }

  @Test
  void 메일_호스트가_있어도_발신_주소가_비면_로그_스텁을_쓴다() {
    runner
        .withPropertyValues(
            "spring.mail.host=smtp.gmail.com", "carix.auth.mail.base-url=https://carix.example.com")
        .run(
            context ->
                assertThat(context.getBean(MagicLinkNotifier.class))
                    .isInstanceOf(LoggingMagicLinkNotifier.class));
  }

  @Test
  void 설정이_모두_있으면_SMTP_발송을_쓴다() {
    runner
        .withPropertyValues(
            "spring.mail.host=smtp.gmail.com",
            "carix.auth.mail.base-url=https://carix.example.com",
            "carix.auth.mail.from=beta@example.com")
        .run(
            context ->
                assertThat(context.getBean(MagicLinkNotifier.class))
                    .isInstanceOf(SmtpMagicLinkNotifier.class));
  }
}
