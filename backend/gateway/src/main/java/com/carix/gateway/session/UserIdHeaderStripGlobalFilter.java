package com.carix.gateway.session;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 인바운드 {@code X-User-Id} 를 무조건 벗긴다.
 *
 * <p>하류 서비스는 이 헤더를 인증된 사용자로 신뢰한다(SECURITY §3.2). 따라서 헤더의 출처는 오직 게이트웨이여야 한다 — 벗기지 않으면 외부에서 임의 UUID 를
 * 실어 보내 그 사용자로 행세할 수 있다.
 *
 * <p>{@link SessionValidationGatewayFilterFactory} 의 기능 플래그와 무관하게 항상 동작해야 한다. 플래그가 꺼져 있을 때 그 필터는
 * 요청을 그대로 통과시키므로, 벗기는 일을 그쪽에 맡기면 플래그가 꺼진 기간 동안 스푸핑이 열린다.
 *
 * <p>전 라우트에 적용한다. 세션 검증 필터가 붙지 않는 auth·web 라우트도 하류에 헤더를 넘길 이유가 없다.
 */
@Component
public class UserIdHeaderStripGlobalFilter implements GlobalFilter, Ordered {

  public static final String HEADER = "X-User-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!exchange.getRequest().getHeaders().containsHeader(HEADER)) {
      return chain.filter(exchange);
    }

    ServerHttpRequest mutatedRequest =
        exchange.getRequest().mutate().headers(headers -> headers.remove(HEADER)).build();
    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  @Override
  public int getOrder() {
    // 라우트 필터(SessionValidation)보다 먼저 돌아야 한다. 그래야 세션 검증이 주입한 값이 벗겨지지 않는다.
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
