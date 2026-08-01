package com.carix.authservice.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.AbstractIntegrationTest;
import com.carix.authservice.user.User;
import com.carix.authservice.user.UserService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SessionControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private UserService userService;

	@Test
	void 로그아웃하면_세션이_폐기되고_쿠키가_지워진다() throws Exception {
		UUID userId = createTestUser();
		String token = sessionService.createSession(userId);

		mockMvc.perform(post("/v1/auth/logout").cookie(new Cookie("session_token", token)))
				.andExpect(status().isOk())
				.andExpect(cookie().maxAge("session_token", 0));

		org.assertj.core.api.Assertions.assertThat(sessionService.findUserIdBySessionToken(token)).isEmpty();
	}

	@Test
	void 전체_로그아웃하면_해당_사용자의_모든_세션이_폐기된다() throws Exception {
		UUID userId = createTestUser();
		String tokenA = sessionService.createSession(userId);
		String tokenB = sessionService.createSession(userId);

		mockMvc.perform(post("/v1/auth/logout-all").cookie(new Cookie("session_token", tokenA)))
				.andExpect(status().isOk());

		org.assertj.core.api.Assertions.assertThat(sessionService.findUserIdBySessionToken(tokenA)).isEmpty();
		org.assertj.core.api.Assertions.assertThat(sessionService.findUserIdBySessionToken(tokenB)).isEmpty();
	}

	@Test
	void 쿠키_없이_로그아웃해도_200을_반환한다() throws Exception {
		mockMvc.perform(post("/v1/auth/logout"))
				.andExpect(status().isOk());
	}

	@Test
	void 유효한_세션이면_200과_사용자ID를_반환한다() throws Exception {
		UUID userId = createTestUser();
		String token = sessionService.createSession(userId);

		mockMvc.perform(get("/v1/auth/session").cookie(new Cookie("session_token", token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(userId.toString()));
	}

	@Test
	void 쿠키가_없으면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/v1/auth/session"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 존재하지_않는_토큰이면_401을_반환한다() throws Exception {
		mockMvc.perform(get("/v1/auth/session").cookie(new Cookie("session_token", "no-such-token")))
				.andExpect(status().isUnauthorized());
	}

	private UUID createTestUser() {
		User user = userService.findOrCreate("session-controller-test-" + UUID.randomUUID() + "@example.com");
		return user.getId();
	}
}
