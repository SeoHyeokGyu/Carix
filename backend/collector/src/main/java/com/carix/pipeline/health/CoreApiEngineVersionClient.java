package com.carix.pipeline.health;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** core-api {@code /api/v1/meta/versions} 를 서비스 간 호출로 읽는다. 게이트웨이의 core 라우트는 세션을 요구해 거치지 않는다. */
@Slf4j
@Component
public class CoreApiEngineVersionClient implements EngineVersionClient {

  private final RestClient restClient;

  public CoreApiEngineVersionClient(
      @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(2));
    this.restClient = builder.baseUrl("http://core-api").requestFactory(factory).build();
  }

  @Override
  public Optional<String> ruleVersion() {
    try {
      @SuppressWarnings("unchecked")
      Map<String, String> body =
          restClient.get().uri("/api/v1/meta/versions").retrieve().body(Map.class);
      return Optional.ofNullable(body).map(b -> b.get("ruleVersion"));
    } catch (Exception e) {
      log.warn("[데이터 상태] 엔진 규칙 버전 조회 실패: {}", e.getMessage());
      return Optional.empty();
    }
  }

  @Configuration
  static class LoadBalancedRestClientConfig {
    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
      return RestClient.builder();
    }
  }
}
