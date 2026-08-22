package com.carix.gateway.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 세션 검증 결과의 인메모리 캐시. TTL 30초는 spring-cloud-architecture.md §4 정책값이다. 유효한 결과만 캐시한다 — 무효 판정을 캐시하면 로그인
 * 직후 발급된 세션이 그 이전의 무효 판정 TTL 동안 계속 거부될 수 있다.
 */
@Component
public class SessionValidationCache {

  private static final Duration TTL = Duration.ofSeconds(30);

  private final Map<String, CacheEntry> entries = new ConcurrentHashMap<>();
  private final Clock clock;

  public SessionValidationCache() {
    this(Clock.systemUTC());
  }

  SessionValidationCache(Clock clock) {
    this.clock = clock;
  }

  public Optional<UUID> getIfValid(String sessionToken) {
    CacheEntry entry = entries.get(sessionToken);
    if (entry == null || entry.expiresAt().isBefore(clock.instant())) {
      return Optional.empty();
    }
    return Optional.of(entry.userId());
  }

  public void put(String sessionToken, UUID userId) {
    entries.put(sessionToken, new CacheEntry(userId, clock.instant().plus(TTL)));
  }

  private record CacheEntry(UUID userId, Instant expiresAt) {}
}
