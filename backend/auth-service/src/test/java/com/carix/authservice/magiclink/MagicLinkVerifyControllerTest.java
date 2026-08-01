package com.carix.authservice.magiclink;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.AbstractIntegrationTest;
import com.carix.authservice.invitation.Invitation;
import com.carix.authservice.invitation.InvitationRepository;
import com.carix.authservice.user.User;
import com.carix.authservice.user.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class MagicLinkVerifyControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InvitationRepository invitationRepository;

	@Autowired
	private UserService userService;

	@Autowired
	private MagicLinkService magicLinkService;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void 유효한_토큰으로_검증하면_세션_쿠키와_사용자_정보를_반환한다() throws Exception {
		String email = "verify-a@example.com";
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		invitationRepository.save(new Invitation(UUID.randomUUID(), email, "PENDING", now.plusDays(7), now));
		User user = userService.findOrCreate(email);
		String rawToken = magicLinkService.issueToken(user.getId());

		mockMvc.perform(post("/v1/auth/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "token", rawToken))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.isOnboardingComplete").value(false))
				.andExpect(cookie().exists("session_token"))
				.andExpect(cookie().httpOnly("session_token", true));
	}

	@Test
	void 잘못된_토큰이면_400을_반환한다() throws Exception {
		mockMvc.perform(post("/v1/auth/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"someone@example.com\",\"token\":\"bogus\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MAGIC_LINK_EXPIRED"));
	}
}
