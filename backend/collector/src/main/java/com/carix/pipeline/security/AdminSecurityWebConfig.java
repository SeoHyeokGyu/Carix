package com.carix.pipeline.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 수집기 관리자 인터셉터 등록 WebMvcConfigurer. */
@Configuration
@RequiredArgsConstructor
public class AdminSecurityWebConfig implements WebMvcConfigurer {

  private final AdminKeyInterceptor adminKeyInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(adminKeyInterceptor).addPathPatterns("/internal/pipeline/**");
  }
}
