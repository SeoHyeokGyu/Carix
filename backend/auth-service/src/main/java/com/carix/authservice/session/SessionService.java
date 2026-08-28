package com.carix.authservice.session;

import com.carix.authservice.common.TokenHasher;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 만료(30일)는 SECURITY.md §1 정책값이다. 튜닝 요구가 없어 상수로 둔다(YAGNI).
 *
 * <p>토큰 원본은 쿠키에만 있고 DB 에는 해시만 저장한다. 조회는 제시된 토큰을 해싱해 대조한다.
 */
@Service
public class SessionService {

  private static final Duration SESSION_TTL = Duration.ofDays(30);

  private final SessionRepository sessionRepository;
  private final SecureRandom secureRandom = new SecureRandom();

  public SessionService(SessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  public String createSession(UUID userId) {
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    String sessionToken = HexFormat.of().formatHex(randomBytes);

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Session session =
        new Session(
            UUID.randomUUID(),
            userId,
            TokenHasher.sha256Hex(sessionToken),
            now.plus(SESSION_TTL),
            now);
    sessionRepository.save(session);

    return sessionToken;
  }

  public Optional<UUID> findUserIdBySessionToken(String sessionToken) {
    return sessionRepository
        .findByTokenHash(TokenHasher.sha256Hex(sessionToken))
        .filter(session -> session.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
        .map(Session::getUserId);
  }

  @Transactional
  public void revoke(String sessionToken) {
    sessionRepository.deleteByTokenHash(TokenHasher.sha256Hex(sessionToken));
  }

  @Transactional
  public void revokeAll(UUID userId) {
    sessionRepository.deleteByUserId(userId);
  }
}
