package com.carix.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import reactor.netty.http.client.HttpClient;

/**
 * {@link DnsCacheTtlConfig} 가 컨텍스트에 실제로 올라오는지 본다.
 *
 * <p>단위 테스트({@link DnsCacheTtlConfigTest})는 커스터마이저가 제 일을 한다는 것만 증명한다. 애너테이션을 빠뜨리거나 컴포넌트 스캔 밖에 두면 그
 * 테스트는 그대로 통과하면서 운영에서는 아무것도 안 죈다.
 */
@SpringBootTest
class DnsCacheTtlWiringTest {

  @Autowired(required = false)
  private HttpClientCustomizer[] customizers;

  @Test
  @DisplayName("DNS 캐시 커스터마이저가 빈으로 등록돼 있다")
  void customizerIsRegistered() {
    assertThat(customizers).isNotNull();

    HttpClient client = HttpClient.create();
    for (HttpClientCustomizer customizer : customizers) {
      client = customizer.customize(client);
    }

    // 등록된 커스터마이저를 전부 통과시킨 결과에 상한이 걸려 있어야 한다. 특정
    // 빈 이름이 아니라 결과를 보므로, 나중에 다른 커스터마이저가 늘어도 이
    // 단언은 "상한이 살아 있는가" 만 묻는다.
    assertThat(client.configuration().getNameResolverProvider()).isNotNull();
    assertThat(client.configuration().getNameResolverProvider().cacheMaxTimeToLive())
        .isEqualTo(Duration.ofSeconds(30));
  }
}
