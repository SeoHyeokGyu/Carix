package com.carix.snapshot;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 운영 발급기를 등록한다. {@code @ConditionalOnMissingBean} 이라 테스트가 고정 발급기를 먼저 올리면 이 빈은 뜨지 않는다 — {@code
 * IndexProviderStubConfiguration} 이 쓰는 방식과 같다. {@code @Component} 에 조건을 직접 붙이면 컴포넌트 스캔 순서에 따라 조건이
 * 평가돼 실제 구현이 있어도 남을 수 있다.
 */
@Configuration
public class DataVersionIssuerConfiguration {

  @Bean
  @ConditionalOnMissingBean(DataVersionIssuer.class)
  DataVersionIssuer postgresDataVersionIssuer(EntityManager entityManager) {
    return new PostgresDataVersionIssuer(entityManager, Clock.systemUTC());
  }
}
