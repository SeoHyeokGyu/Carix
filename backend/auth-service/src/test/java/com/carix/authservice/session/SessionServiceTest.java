package com.carix.authservice.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.authservice.AbstractIntegrationTest;
import com.carix.authservice.user.User;
import com.carix.authservice.user.UserService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SessionServiceTest extends AbstractIntegrationTest {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private UserService userService;

	@Test
	void 세션을_만들면_토큰으로_사용자_ID를_찾을_수_있다() {
		UUID userId = createTestUser();
		String token = sessionService.createSession(userId);

		assertThat(sessionService.findUserIdBySessionToken(token)).contains(userId);
	}

	@Test
	void 세션을_폐기하면_더_이상_찾을_수_없다() {
		UUID userId = createTestUser();
		String token = sessionService.createSession(userId);

		sessionService.revoke(token);

		assertThat(sessionService.findUserIdBySessionToken(token)).isEmpty();
	}

	@Test
	void 전체_폐기하면_사용자의_모든_세션이_사라진다() {
		UUID userId = createTestUser();
		String tokenA = sessionService.createSession(userId);
		String tokenB = sessionService.createSession(userId);

		sessionService.revokeAll(userId);

		assertThat(sessionService.findUserIdBySessionToken(tokenA)).isEmpty();
		assertThat(sessionService.findUserIdBySessionToken(tokenB)).isEmpty();
	}

	private UUID createTestUser() {
		User user = userService.findOrCreate("session-test-" + UUID.randomUUID() + "@example.com");
		return user.getId();
	}
}
