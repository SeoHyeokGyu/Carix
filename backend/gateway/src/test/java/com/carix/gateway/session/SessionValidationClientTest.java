package com.carix.gateway.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

  @Test
  void 유효한_세션이면_사용자ID를_반환한다() {
    UUID userId = UUID.randomUUID();
    WebClient.Builder builder =
        WebClient.builder()
            .exchangeFunction(
                request -> {
                  assertThat(request.url().getPath()).isEqualTo("/api/v1/auth/session");
                  assertThat(request.cookies().getFirst("session_token")).isEqualTo("valid-token");
                  return Mono.just(
                      ClientResponse.create(HttpStatus.OK)
                          .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                          .body("{\"userId\":\"" + userId + "\"}")
                          .build());
                });
    SessionValidationClient client = new SessionValidationClient(builder);

    assertThat(client.validate("valid-token").block()).isEqualTo(userId);
  }
}
