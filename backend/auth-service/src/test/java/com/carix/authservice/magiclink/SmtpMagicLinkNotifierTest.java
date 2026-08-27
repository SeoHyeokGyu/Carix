package com.carix.authservice.magiclink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpMagicLinkNotifierTest {

  private static final MagicLinkMailProperties PROPERTIES =
      new MagicLinkMailProperties("https://carix.example.com", "beta@example.com", "Carix");

  private JavaMailSender mailSender;
  private SmtpMagicLinkNotifier notifier;

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
    given(mailSender.createMimeMessage())
        .willAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    notifier = new SmtpMagicLinkNotifier(mailSender, PROPERTIES);
  }

  @Test
  void 링크는_프런트엔드_검증_경로를_가리킨다() {
    assertThat(notifier.buildLink("abc123"))
        .isEqualTo("https://carix.example.com/auth/verify?token=abc123");
  }

  @Test
  void base_url_끝의_슬래시가_중복되지_않는다() {
    SmtpMagicLinkNotifier withSlash =
        new SmtpMagicLinkNotifier(
            mailSender,
            new MagicLinkMailProperties("https://carix.example.com/", "beta@example.com", "Carix"));

    assertThat(withSlash.buildLink("abc123"))
        .isEqualTo("https://carix.example.com/auth/verify?token=abc123");
  }

  @Test
  void 발송_실패를_호출부로_던지지_않는다_계정_열거를_막기_위해서다() {
    willThrow(new MailSendException("smtp down")).given(mailSender).send(any(MimeMessage.class));

    assertThatCode(() -> notifier.send("beta@example.com", "abc123")).doesNotThrowAnyException();
  }

  @Test
  void 로그용_이메일은_마스킹된다() {
    assertThat(SmtpMagicLinkNotifier.mask("beta@example.com")).isEqualTo("b***@example.com");
    assertThat(SmtpMagicLinkNotifier.mask("a@b.kr")).isEqualTo("a***@b.kr");
    assertThat(SmtpMagicLinkNotifier.mask("@no-local.com")).isEqualTo("***");
    assertThat(SmtpMagicLinkNotifier.mask("")).isEqualTo("(없음)");
    assertThat(SmtpMagicLinkNotifier.mask(null)).isEqualTo("(없음)");
  }

  @Test
  void 발신자_이름이_비면_Carix_로_채운다() {
    MagicLinkMailProperties blankName =
        new MagicLinkMailProperties("https://carix.example.com", "beta@example.com", "  ");

    assertThat(blankName.fromName()).isEqualTo("Carix");
  }
}
