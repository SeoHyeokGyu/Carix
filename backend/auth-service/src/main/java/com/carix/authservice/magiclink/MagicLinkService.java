package com.carix.authservice.magiclink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 매직링크 만료(15분)는 SECURITY.md §1 정책값이다. 튜닝 요구가 없어 상수로 둔다(YAGNI).
 */
@Service
public class MagicLinkService {

	private static final Duration TOKEN_TTL = Duration.ofMinutes(15);

	private final MagicLinkTokenRepository tokenRepository;
	private final SecureRandom secureRandom = new SecureRandom();

	public MagicLinkService(MagicLinkTokenRepository tokenRepository) {
		this.tokenRepository = tokenRepository;
	}

	public String issueToken(UUID userId) {
		byte[] randomBytes = new byte[32];
		secureRandom.nextBytes(randomBytes);
		String rawToken = HexFormat.of().formatHex(randomBytes);

		MagicLinkToken token = new MagicLinkToken(
				UUID.randomUUID(),
				userId,
				hash(rawToken),
				OffsetDateTime.now(ZoneOffset.UTC).plus(TOKEN_TTL));
		tokenRepository.save(token);

		return rawToken;
	}

	public UUID verifyToken(String rawToken) {
		MagicLinkToken token = tokenRepository.findByTokenHash(hash(rawToken))
				.orElseThrow(MagicLinkInvalidException::new);

		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
			throw new MagicLinkInvalidException();
		}

		token.markUsed(now);
		tokenRepository.save(token);
		return token.getUserId();
	}

	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
