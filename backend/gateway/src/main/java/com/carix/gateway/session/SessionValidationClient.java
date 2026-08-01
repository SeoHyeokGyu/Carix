package com.carix.gateway.session;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * auth-service 의 GET /api/v1/auth/session 을 호출한다. lb:// 스킴 해석과 같은 방식으로
 * 로드밸런싱하도록 @LoadBalanced WebClient.Builder(WebClientConfig)를 주입받는다.
 */
@Component
public class SessionValidationClient implements SessionValidator {

	private final WebClient webClient;

	public SessionValidationClient(WebClient.Builder loadBalancedWebClientBuilder) {
		this.webClient = loadBalancedWebClientBuilder.baseUrl("http://auth-service").build();
	}

	@Override
	public Mono<UUID> validate(String sessionToken) {
		return webClient.get()
				.uri("/api/v1/auth/session")
				.cookie("session_token", sessionToken)
				.exchangeToMono(response -> {
					if (response.statusCode().is2xxSuccessful()) {
						return response.bodyToMono(SessionValidationBody.class)
								.map(SessionValidationBody::userId);
					}
					return Mono.empty();
				});
	}

	private record SessionValidationBody(UUID userId) {
	}
}
