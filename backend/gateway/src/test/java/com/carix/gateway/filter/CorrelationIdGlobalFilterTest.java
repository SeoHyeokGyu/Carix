package com.carix.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class CorrelationIdGlobalFilterTest {

	private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

	@Test
	void 요청에_상관관계ID가_없으면_새로_생성해_요청과_응답에_붙인다() {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/health"));
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		filter.filter(exchange, ex -> {
			forwarded.set(ex);
			return Mono.empty();
		}).block();

		assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER)).isNotBlank();
		assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER)).isNotBlank();
	}

	@Test
	void 요청에_상관관계ID가_이미_있으면_그대로_전파한다() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/health").header(CorrelationIdGlobalFilter.HEADER, "existing-id"));
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		filter.filter(exchange, ex -> {
			forwarded.set(ex);
			return Mono.empty();
		}).block();

		assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER))
				.isEqualTo("existing-id");
		assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER)).isEqualTo("existing-id");
	}
}
