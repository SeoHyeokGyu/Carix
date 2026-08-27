package com.carix.authservice.magiclink;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 메일 설정이 갖춰졌을 때만 SMTP 발송을 켠다.
 *
 * <p>{@code @ConditionalOnBean(JavaMailSender.class)} 를 쓰지 않는다 — 사용자 {@code @Configuration} 은
 * 자동설정보다 먼저 평가돼 조건이 항상 거짓이 된다. {@link ObjectProvider} 는 빈 생성 시점에 해석되므로 순서에 영향받지 않는다.
 */
@Configuration
@EnableConfigurationProperties(MagicLinkMailProperties.class)
public class MagicLinkNotifierConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MagicLinkNotifierConfiguration.class);

  @Bean
  MagicLinkNotifier magicLinkNotifier(
      ObjectProvider<JavaMailSender> mailSender, MagicLinkMailProperties properties) {

    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.warn("spring.mail.host 가 없어 매직링크를 로그로만 남깁니다. 실제 메일은 발송되지 않습니다.");
      return new LoggingMagicLinkNotifier();
    }
    if (!properties.isComplete()) {
      log.warn("carix.auth.mail.base-url 또는 from 이 비어 매직링크를 로그로만 남깁니다. 실제 메일은 발송되지 않습니다.");
      return new LoggingMagicLinkNotifier();
    }

    log.info("매직링크를 SMTP 로 발송합니다. 발신={} 링크 기준={}", properties.from(), properties.baseUrl());
    return new SmtpMagicLinkNotifier(sender, properties);
  }
}
