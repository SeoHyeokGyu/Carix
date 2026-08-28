package com.carix.authservice.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.authservice.common.TokenHasher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private SessionRepository repository;
  private SessionService service;

  @BeforeEach
  void setUp() {
    repository = mock(SessionRepository.class);
    given(repository.save(any(Session.class))).willAnswer(i -> i.getArgument(0));
    service = new SessionService(repository);
  }

  @Test
  void 저장하는_것은_원본이_아니라_해시다() {
    String rawToken = service.createSession(USER_ID);

    ArgumentCaptor<Session> saved = ArgumentCaptor.forClass(Session.class);
    verify(repository).save(saved.capture());

    assertThat(saved.getValue().getTokenHash())
        .isEqualTo(TokenHasher.sha256Hex(rawToken))
        .isNotEqualTo(rawToken);
  }

  @Test
  void 발급한_원본은_32바이트_난수_hex_다() {
    assertThat(service.createSession(USER_ID)).matches("[0-9a-f]{64}");
  }

  @Test
  void 조회는_제시된_토큰을_해싱해_대조한다() {
    String rawToken = "a".repeat(64);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    given(repository.findByTokenHash(TokenHasher.sha256Hex(rawToken)))
        .willReturn(
            Optional.of(
                new Session(
                    UUID.randomUUID(),
                    USER_ID,
                    TokenHasher.sha256Hex(rawToken),
                    now.plusDays(30),
                    now)));

    assertThat(service.findUserIdBySessionToken(rawToken)).contains(USER_ID);
  }

  @Test
  void 만료된_세션은_찾지_못한다() {
    String rawToken = "b".repeat(64);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    given(repository.findByTokenHash(TokenHasher.sha256Hex(rawToken)))
        .willReturn(
            Optional.of(
                new Session(
                    UUID.randomUUID(),
                    USER_ID,
                    TokenHasher.sha256Hex(rawToken),
                    now.minusMinutes(1),
                    now.minusDays(31))));

    assertThat(service.findUserIdBySessionToken(rawToken)).isEmpty();
  }

  @Test
  void 폐기도_해시로_지운다() {
    String rawToken = "c".repeat(64);

    service.revoke(rawToken);

    verify(repository).deleteByTokenHash(TokenHasher.sha256Hex(rawToken));
  }
}
