package com.carix.gateway.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class SessionValidationGatewayFilterFactoryTest {

	@Test
	void 기능플래그가_꺼져있으면_검증없이_통과시킨다() {
		SessionValidationProperties properties = new SessionValidationProperties();
		properties.setEnabled(false);
		StubValidator validator = new StubValidator(null);
		GatewayFilter filter = new SessionValidationGatewayFilterFactory(properties, new SessionValidationCache(), validator)
				.apply(new SessionValidationGatewayFilterFactory.Config());

		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/expenses"));
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		filter.filter(exchange, ex -> {
			forwarded.set(ex);
			return Mono.empty();
		}).block();

		assertThat(forwarded.get()).isNotNull();
		assertThat(validator.called).isFalse();
	}

	@Test
	void 쿠키가_없으면_401을_반환하고_체인을_호출하지_않는다() {
		SessionValidationProperties properties = new SessionValidationProperties();
		properties.setEnabled(true);
		GatewayFilter filter = new SessionValidationGatewayFilterFactory(
				properties, new SessionValidationCache(), new StubValidator(null))
				.apply(new SessionValidationGatewayFilterFactory.Config());

		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/expenses"));
		AtomicBoolean chainCalled = new AtomicBoolean(false);

		filter.filter(exchange, ex -> {
			chainCalled.set(true);
			return Mono.empty();
		}).block();

		assertThat(chainCalled.get()).isFalse();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void 유효한_세션이면_X_User_Id_헤더를_붙여_통과시킨다() {
		UUID userId = UUID.randomUUID();
		SessionValidationProperties properties = new SessionValidationProperties();
		properties.setEnabled(true);
		GatewayFilter filter = new SessionValidationGatewayFilterFactory(
				properties, new SessionValidationCache(), new StubValidator(userId))
				.apply(new SessionValidationGatewayFilterFactory.Config());

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/v1/expenses").cookie(new HttpCookie("session_token", "valid-token")));
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		filter.filter(exchange, ex -> {
			forwarded.set(ex);
			return Mono.empty();
		}).block();

		assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo(userId.toString());
	}

	@Test
	void 무효한_세션이면_401을_반환한다() {
		SessionValidationProperties properties = new SessionValidationProperties();
		properties.setEnabled(true);
		GatewayFilter filter = new SessionValidationGatewayFilterFactory(
				properties, new SessionValidationCache(), new StubValidator(null))
				.apply(new SessionValidationGatewayFilterFactory.Config());

		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/v1/expenses").cookie(new HttpCookie("session_token", "invalid-token")));
		AtomicBoolean chainCalled = new AtomicBoolean(false);

		filter.filter(exchange, ex -> {
			chainCalled.set(true);
			return Mono.empty();
		}).block();

		assertThat(chainCalled.get()).isFalse();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	private static final class StubValidator implements SessionValidator {
		private final UUID result;
		private boolean called;

		StubValidator(UUID result) {
			this.result = result;
		}

		@Override
		public Mono<UUID> validate(String sessionToken) {
			called = true;
			return result == null ? Mono.empty() : Mono.just(result);
		}
	}
}
