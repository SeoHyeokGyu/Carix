package com.carix.authservice.magiclink;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 매직링크 메일 발송 설정.
 *
 * @param baseUrl 링크의 앞부분. 프런트엔드 진입점이다. 예: {@code https://carix.example.com}
 * @param from 발신 주소. Gmail SMTP 를 쓰면 인증에 사용한 계정과 같아야 한다 — 다르면 Gmail 이 조용히 계정 주소로 바꿔 보낸다
 * @param fromName 수신함에 표시될 발신자 이름
 */
@ConfigurationProperties(prefix = "carix.auth.mail")
public record MagicLinkMailProperties(String baseUrl, String from, String fromName) {

  public MagicLinkMailProperties {
    fromName = (fromName == null || fromName.isBlank()) ? "Carix" : fromName;
  }

  /** 발송에 필요한 값이 다 있는지. 하나라도 비면 SMTP 발송을 켜지 않는다. */
  public boolean isComplete() {
    return baseUrl != null && !baseUrl.isBlank() && from != null && !from.isBlank();
  }
}
