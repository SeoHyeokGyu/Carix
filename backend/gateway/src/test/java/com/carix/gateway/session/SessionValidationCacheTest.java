package com.carix.gateway.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionValidationCacheTest {

	@Test
	void TTL_이내면_저장한_사용자ID를_반환한다() {
		MutableClock clock = new MutableClock(Instant.parse("2026-08-01T00:00:00Z"));
		SessionValidationCache cache = new SessionValidationCache(clock);
		UUID userId = UUID.randomUUID();

		cache.put("token-a", userId);
		clock.advance(Duration.ofSeconds(29));

		assertThat(cache.getIfValid("token-a")).contains(userId);
	}

	@Test
	void TTL_30초가_지나면_만료로_취급한다() {
		MutableClock clock = new MutableClock(Instant.parse("2026-08-01T00:00:00Z"));
		SessionValidationCache cache = new SessionValidationCache(clock);
		UUID userId = UUID.randomUUID();

		cache.put("token-a", userId);
		clock.advance(Duration.ofSeconds(31));

		assertThat(cache.getIfValid("token-a")).isEmpty();
	}

	@Test
	void 캐시에_없는_토큰은_빈값을_반환한다() {
		SessionValidationCache cache = new SessionValidationCache(Clock.fixed(Instant.now(), ZoneOffset.UTC));

		assertThat(cache.getIfValid("unknown")).isEmpty();
	}

	private static final class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			this.instant = this.instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
