package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ApplicationContext;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

/**
 * Eureka 클라이언트는 {@code ObjectProvider<RestClient.Builder>.getIfAvailable(RestClient::builder)} 로 전송
 * 빌더를 고른다. 로드밸런스 빌더가 타입만으로 잡히면 Eureka 서버 주소를 서비스 id 로 풀어 등록이 깨지므로, 한정자 없는 조회에는 보이지 않아야 한다.
 */
@SpringBootTest
class EngineVersionClientWiringTest {

  @Autowired private ObjectProvider<RestClient.Builder> unqualifiedBuilders;
  @Autowired @LoadBalanced private RestClient.Builder loadBalancedBuilder;
  @Autowired private ApplicationContext context;

  @Test
  @DisplayName("한정자 없는 RestClient.Builder 조회에는 로드밸런스 빌더가 잡히지 않는다")
  void unqualifiedLookupDoesNotSeeLoadBalancedBuilder() {
    assertThat(unqualifiedBuilders.getIfAvailable()).isNull();
  }

  @Test
  @DisplayName("@LoadBalanced 주입 지점은 로드밸런스 빌더를 받고 엔진 버전 클라이언트가 뜬다")
  void qualifiedInjectionStillWorks() {
    assertThat(loadBalancedBuilder).isNotNull();
    assertThat(context.getBean("loadBalancedRestClientBuilder")).isSameAs(loadBalancedBuilder);
    assertThat(context.getBean(CoreApiEngineVersionClient.class)).isNotNull();
    List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
    loadBalancedBuilder.clone().requestInterceptors(interceptors::addAll);
    assertThat(interceptors)
        .as("로드밸런서 후처리가 기본 후보가 아닌 빌더에도 걸려야 한다")
        .anyMatch(i -> i.getClass().getSimpleName().contains("LoadBalancer"));
  }
}
