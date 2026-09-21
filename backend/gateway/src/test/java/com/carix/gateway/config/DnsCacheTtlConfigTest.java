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

  @Test
  @DisplayName("죄기 전 기본값은 30초보다 길다 — 이 수정이 실제로 무언가를 바꾼다")
  void defaultIsLongerThanTheCap() {
    // 기본값을 그대로 두면 Docker 내장 DNS 가 주는 TTL 600 을 그대로 지킨다.
    // 이 단언이 깨지면(기본값이 30초 이하로 바뀌면) 위 커스터마이저는 더 이상
    // 아무것도 막지 않는다 — 그때는 이 설정을 지워야 한다.
    //
    // HttpClient.create().configuration().getNameResolverProvider() 로는 못 읽는다.
    // 리졸버를 명시하지 않으면 그 필드가 null 이고 전송 계층이 내부 기본값으로
    // 떨어지기 때문이다. 공개 빌더가 같은 기본값을 만든다.
    NameResolverProvider defaults = NameResolverProvider.builder().build();

    assertThat(defaults.cacheMaxTimeToLive()).isGreaterThan(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("커스터마이저는 캐시 상한만 바꾸고 다른 리졸버 설정은 기본값으로 둔다")
  void touchesOnlyTheCap() {
    NameResolverProvider defaults = NameResolverProvider.builder().build();

    NameResolverProvider customized =
        config
            .dnsCacheTtlCustomizer()
            .customize(HttpClient.create())
            .configuration()
            .getNameResolverProvider();

    assertThat(customized.cacheMinTimeToLive()).isEqualTo(defaults.cacheMinTimeToLive());
    assertThat(customized.cacheNegativeTimeToLive()).isEqualTo(defaults.cacheNegativeTimeToLive());
  }
}
