package com.carix.authservice.magiclink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 임시 스텁. 실제 이메일 provider 연동 전까지 로그로만 남긴다.
 *
 * <p>주의: 로그에 원본 토큰이 남으므로, 이 구현을 실제 운영 발송 경로로 쓰지 않는다. {@code spring.mail.host} 와 {@code
 * carix.auth.mail.*} 이 채워지면 {@link MagicLinkNotifierConfiguration} 이 SMTP 구현을 대신 등록한다.
 */
public class LoggingMagicLinkNotifier implements MagicLinkNotifier {

  private static final Logger log = LoggerFactory.getLogger(LoggingMagicLinkNotifier.class);

  @Override
  public void send(String email, String rawToken) {
    log.info("[STUB] 매직링크 발송 대상={} 토큰={} (실제 이메일 미발송 — provider 미정)", email, rawToken);
  }
}
