package com.carix.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt 인코더만 등록한다.
 *
 * <p>Spring Security 필터 체인이나 {@code UserDetailsService} 는 두지 않는다 — 인증은 세션 쿠키가 이미 하고 있고, 여기서 필요한 것은
 * 비밀번호 해싱뿐이다. 그래서 starter 가 아니라 {@code spring-security-crypto} 만 의존한다.
 */
@Configuration
public class PasswordEncoderConfiguration {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
