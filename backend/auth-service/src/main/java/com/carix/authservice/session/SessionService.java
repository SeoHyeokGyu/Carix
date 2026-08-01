package com.carix.authservice.session;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 세션 만료(30일)는 SECURITY.md §1 정책값이다. 튜닝 요구가 없어 상수로 둔다(YAGNI).
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
		Session session = new Session(UUID.randomUUID(), userId, sessionToken, now.plus(SESSION_TTL), now);
		sessionRepository.save(session);

		return sessionToken;
	}

	public Optional<UUID> findUserIdBySessionToken(String sessionToken) {
		return sessionRepository.findBySessionToken(sessionToken)
				.filter(session -> session.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)))
				.map(Session::getUserId);
	}

	public void revoke(String sessionToken) {
		sessionRepository.deleteBySessionToken(sessionToken);
	}

	public void revokeAll(UUID userId) {
		sessionRepository.deleteByUserId(userId);
	}
}
