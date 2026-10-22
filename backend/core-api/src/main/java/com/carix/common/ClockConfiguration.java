package com.carix.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각이 필요한 서비스가 주입받는 시계. 테스트는 고정 시계를 직접 넣는다. */
@Configuration
public class ClockConfiguration {

  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }
}
