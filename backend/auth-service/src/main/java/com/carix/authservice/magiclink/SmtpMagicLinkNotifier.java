package com.carix.authservice.magiclink;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP 로 매직링크를 보낸다. Gmail SMTP 를 전제로 하지만 provider 에 묶인 코드는 없다 — {@code spring.mail.*} 만 바꾸면 다른 SMTP
 * 로 옮겨간다.
 *
 * <p>발송 실패를 호출부로 던지지 않는다. 던지면 초대된 이메일만 5xx 가 나서 초대 여부가 응답으로 드러난다 — 계정 열거 방지(SECURITY.md §1)가 깨진다.
 * 실패는 ERROR 로그로만 남기고 정상 응답을 유지한다.
 */
public class SmtpMagicLinkNotifier implements MagicLinkNotifier {

  private static final Logger log = LoggerFactory.getLogger(SmtpMagicLinkNotifier.class);
  private static final String SUBJECT = "[Carix] 로그인 링크입니다";

  private final JavaMailSender mailSender;
  private final MagicLinkMailProperties properties;

  public SmtpMagicLinkNotifier(JavaMailSender mailSender, MagicLinkMailProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(String email, String rawToken) {
    String link = buildLink(rawToken);
    try {
      mailSender.send(compose(email, link));
      log.info("매직링크 메일을 발송했습니다. 수신자={}", email);
    } catch (Exception e) {
      // 토큰은 로그에 남기지 않는다. 링크 전체가 곧 자격증명이다.
      log.error("매직링크 메일 발송에 실패했습니다. 수신자={} 원인={}", email, e.getMessage(), e);
    }
  }

  String buildLink(String rawToken) {
    String base = properties.baseUrl().endsWith("/") ? stripTrailingSlash() : properties.baseUrl();
    return base + "/auth/verify?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
  }

  private String stripTrailingSlash() {
    String base = properties.baseUrl();
    return base.substring(0, base.length() - 1);
  }

  private MimeMessage compose(String email, String link) throws Exception {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
    helper.setFrom(fromAddress());
    helper.setTo(email);
    helper.setSubject(SUBJECT);
    helper.setText(plainBody(link), htmlBody(link));
    return message;
  }

  private InternetAddress fromAddress() throws UnsupportedEncodingException {
    return new InternetAddress(
        properties.from(), properties.fromName(), StandardCharsets.UTF_8.name());
  }

  private String plainBody(String link) {
    return """
        아래 링크로 Carix 에 로그인합니다.

        %s

        이 링크는 15분간 유효하며 한 번만 쓸 수 있습니다.
        요청한 적이 없다면 이 메일을 무시하세요.
        """
        .formatted(link);
  }

  private String htmlBody(String link) {
    return """
        <div style="font-family:sans-serif;line-height:1.6;color:#1f2937">
          <p>아래 버튼으로 Carix 에 로그인합니다.</p>
          <p>
            <a href="%s"
               style="display:inline-block;padding:12px 20px;border-radius:8px;
                      background:#4f46e5;color:#ffffff;text-decoration:none">
              로그인하기
            </a>
          </p>
          <p style="font-size:13px;color:#6b7280">
            버튼이 눌리지 않으면 이 주소를 브라우저에 붙여 넣으세요.<br>
            <span style="word-break:break-all">%s</span>
          </p>
          <p style="font-size:13px;color:#6b7280">
            이 링크는 15분간 유효하며 한 번만 쓸 수 있습니다.
            요청한 적이 없다면 이 메일을 무시하세요.
          </p>
        </div>
        """
        .formatted(link, link);
  }
}
