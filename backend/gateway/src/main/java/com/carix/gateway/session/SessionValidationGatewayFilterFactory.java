package com.carix.gateway.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 라우트 설정에서 필터 이름 "SessionValidation" 으로 참조한다(클래스명에서 GatewayFilterFactory 접미사를 뗀 이름 — Spring Cloud
 * Gateway 관례). config/spring/gateway.yml 의 core 라우트에서만 적용한다 — auth·web 라우트는 우회한다.
 */
@Component
public class SessionValidationGatewayFilterFactory
    extends AbstractGatewayFilterFactory<SessionValidationGatewayFilterFactory.Config> {

  private static final String COOKIE_NAME = "session_token";
  // 인바운드 값은 UserIdHeaderStripGlobalFilter 가 이미 벗겼다. 여기서 넣는 값만 하류에 도달한다.
  private static final String USER_ID_HEADER = UserIdHeaderStripGlobalFilter.HEADER;

  private final SessionValidationProperties properties;
  private final SessionValidationCache cache;
  private final SessionValidator validator;

  public SessionValidationGatewayFilterFactory(
      SessionValidationProperties properties,
      SessionValidationCache cache,
      SessionValidator validator) {
    super(Config.class);
    this.properties = properties;
    this.cache = cache;
    this.validator = validator;
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      if (!properties.isEnabled()) {
        return chain.filter(exchange);
      }

      HttpCookie cookie = exchange.getRequest().getCookies().getFirst(COOKIE_NAME);
      if (cookie == null) {
        return unauthorized(exchange);
      }
      String sessionToken = cookie.getValue();

      Optional<UUID> cached = cache.getIfValid(sessionToken);
      if (cached.isPresent()) {
        return chain.filter(withUserId(exchange, cached.get()));
      }

      return validator
          .validate(sessionToken)
          .map(Optional::of)
          .defaultIfEmpty(Optional.empty())
          .flatMap(
              maybeUserId ->
                  maybeUserId
                      .map(
                          userId -> {
                            cache.put(sessionToken, userId);
                            return chain.filter(withUserId(exchange, userId));
                          })
                      .orElseGet(() -> unauthorized(exchange)));
    };
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
  }

  private ServerWebExchange withUserId(ServerWebExchange exchange, UUID userId) {
    ServerHttpRequest mutatedRequest =
        exchange.getRequest().mutate().header(USER_ID_HEADER, userId.toString()).build();
    return exchange.mutate().request(mutatedRequest).build();
  }

  public static class Config {}
}
