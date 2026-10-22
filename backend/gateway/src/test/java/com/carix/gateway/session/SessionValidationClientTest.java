package com.carix.gateway.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class SessionValidationClientTest {

  @Test
  void 무효한_세션이면_빈값을_반환한다() {
    WebClient.Builder builder =
        WebClient.builder()
            .exchangeFunction(
                request -> {
                  assertThat(request.url().getPath()).isEqualTo("/api/v1/auth/session");
                  assertThat(request.cookies().getFirst("session_token"))
                      .isEqualTo("invalid-token");
                  return Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build());
                });
    SessionValidationClient client = new SessionValidationClient(builder);

    assertThat(client.validate("invalid-token").blockOptional()).isEmpty();
  }
}
