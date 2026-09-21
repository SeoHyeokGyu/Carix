package com.carix.gateway.config;

import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 게이트웨이가 업스트림 이름을 캐시하는 시간을 30초로 죈다.
 *
 * <p>Docker 내장 DNS 는 컨테이너 이름에 TTL 600(10분)을 준다. Reactor Netty 의 비동기 리졸버는 그 TTL 을 그대로 지키므로, 배포가
 * 컨테이너를 재생성해 IP 가 바뀌면 게이트웨이가 최대 10분간 사라진 주소로 붙는다 — {@code Connection refused} 로 502·500 이 난다.
 *
 * <p><b>이 문제는 정적 URI 라우트에만 생긴다.</b> {@code lb://} 라우트(core-api·auth-service·collector)는 Eureka
 * 레지스트리로 다시 찾으므로 재시작해도 무사하다. 반면 {@code frontend-node} 는 Node 앱이라 Eureka 에 등록이 없고 {@code
 * http://frontend-node:3000} 으로 직접 잡혀 이 경로에 그대로 노출된다 ({@code
 * backend/gateway/src/main/resources/application.yml}).
 *
 * <p>JVM 리졸버({@code DefaultAddressResolverGroup})로 갈아타면 {@code networkaddress.cache.ttl} 을 따르지만 그쪽은
 * 블로킹이라 리액티브 게이트웨이에 역행한다. 비동기 리졸버를 유지한 채 상한만 건다.
 */
@Configuration
public class DnsCacheTtlConfig {

  /** 재생성된 컨테이너로 다시 붙기까지 최대 이만큼 걸린다. 배포 헬스체크의 150초 대기 안에 든다. */
  private static final Duration CACHE_MAX_TTL = Duration.ofSeconds(30);

  @Bean
  public HttpClientCustomizer dnsCacheTtlCustomizer() {
    return httpClient -> httpClient.resolver(spec -> spec.cacheMaxTimeToLive(CACHE_MAX_TTL));
  }
}
