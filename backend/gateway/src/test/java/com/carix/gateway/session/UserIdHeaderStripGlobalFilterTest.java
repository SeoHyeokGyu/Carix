package com.carix.gateway.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpCookie;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 하류가 X-User-Id 를 인증된 사용자로 신뢰하므로, 외부에서 들어온 값이 하류에 도달하면 그대로 신원 위조다. 여기서 검증하는 것은 "게이트웨이가 넣은 값만 하류에
 * 간다" 는 속성이다.
 */
class UserIdHeaderStripGlobalFilterTest {

  private static final String HEADER = "X-User-Id";

  private final UserIdHeaderStripGlobalFilter filter = new UserIdHeaderStripGlobalFilter();

  @Test
  void 인바운드_X_User_Id_를_벗긴다() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/expenses")
                .header(HEADER, UUID.randomUUID().toString()));

    ServerWebExchange forwarded = runFilter(exchange);

    assertThat(forwarded.getRequest().getHeaders().headerNames()).doesNotContain(HEADER);
  }

  @Test
  void 헤더가_없으면_요청을_그대로_넘긴다() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/expenses"));

    ServerWebExchange forwarded = runFilter(exchange);

    assertThat(forwarded.getRequest().getHeaders().headerNames()).doesNotContain(HEADER);
  }

  @Test
  void 세션검증이_꺼져있어도_위조_헤더는_하류에_닿지_않는다() {
    SessionValidationProperties properties = new SessionValidationProperties();
    properties.setEnabled(false);

    ServerWebExchange forwarded =
        runStripThenSessionValidation(properties, UUID.randomUUID(), null);

    assertThat(forwarded.getRequest().getHeaders().headerNames()).doesNotContain(HEADER);
  }

  @Test
  void 세션검증이_켜져있으면_위조_헤더가_검증된_사용자로_대체된다() {
    SessionValidationProperties properties = new SessionValidationProperties();
    properties.setEnabled(true);
    UUID spoofed = UUID.randomUUID();
    UUID authenticated = UUID.randomUUID();

    ServerWebExchange forwarded = runStripThenSessionValidation(properties, spoofed, authenticated);

    // 값이 덧붙지 않고 대체됐는지까지 본다 — 하류가 첫 값을 읽으면 위조값이 이길 수 있다.
    assertThat(forwarded.getRequest().getHeaders().get(HEADER))
        .containsExactly(authenticated.toString());
  }

  private ServerWebExchange runFilter(MockServerWebExchange exchange) {
    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    filter
        .filter(
            exchange,
            ex -> {
              forwarded.set(ex);
              return Mono.empty();
            })
        .block();
    return forwarded.get();
  }

  /** strip 필터를 먼저 태우고 그 결과를 세션 검증 필터에 넘긴다 — 운영에서의 실제 순서다. */
  private ServerWebExchange runStripThenSessionValidation(
      SessionValidationProperties properties, UUID spoofed, UUID validatorResult) {

    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/expenses")
                .header(HEADER, spoofed.toString())
                .cookie(new HttpCookie("session_token", "token")));

    GatewayFilter sessionValidation =
        new SessionValidationGatewayFilterFactory(
                properties,
                new SessionValidationCache(),
                token -> validatorResult == null ? Mono.empty() : Mono.just(validatorResult))
            .apply(new SessionValidationGatewayFilterFactory.Config());

    AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
    filter
        .filter(
            exchange,
            stripped ->
                sessionValidation.filter(
                    stripped,
                    ex -> {
                      forwarded.set(ex);
                      return Mono.empty();
                    }))
        .block();
    return forwarded.get();
  }
}
