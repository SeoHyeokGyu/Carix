package com.carix.authservice.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.authservice.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RateLimitServiceTest extends AbstractIntegrationTest {

	@Autowired
	private RateLimitService rateLimitService;

	@Test
	void 한도_이하_요청은_초과로_판정하지_않는다() {
		String key = "test-email-" + UUID.randomUUID();

		for (int i = 0; i < 3; i++) {
			assertThat(rateLimitService.isExceeded("email", key, 5)).isFalse();
		}
	}

	@Test
	void 한도를_넘는_요청은_초과로_판정한다() {
		String key = "test-email-" + UUID.randomUUID();

		for (int i = 0; i < 5; i++) {
			rateLimitService.isExceeded("email", key, 5);
		}

		assertThat(rateLimitService.isExceeded("email", key, 5)).isTrue();
	}

	@Test
	void 서로_다른_scope는_독립적으로_집계된다() {
		String key = "shared-key-" + UUID.randomUUID();

		for (int i = 0; i < 5; i++) {
			rateLimitService.isExceeded("email", key, 5);
		}

		assertThat(rateLimitService.isExceeded("ip", key, 20)).isFalse();
	}
}
