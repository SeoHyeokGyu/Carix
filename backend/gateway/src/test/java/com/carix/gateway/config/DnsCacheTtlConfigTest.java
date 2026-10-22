package com.carix.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.NameResolverProvider;

class DnsCacheTtlConfigTest {

  private final DnsCacheTtlConfig config = new DnsCacheTtlConfig();

  @Test
  @DisplayName("커스터마이저가 이름 해석 캐시 상한을 30초로 죈다")
  void capsCacheMaxTimeToLive() {
    HttpClientCustomizer customizer = config.dnsCacheTtlCustomizer();

    HttpClient customized = customizer.customize(HttpClient.create());

    NameResolverProvider provider = customized.configuration().getNameResolverProvider();
    assertThat(provider).isNotNull();
    assertThat(provider.cacheMaxTimeToLive()).isEqualTo(Duration.ofSeconds(30));
  }
}
