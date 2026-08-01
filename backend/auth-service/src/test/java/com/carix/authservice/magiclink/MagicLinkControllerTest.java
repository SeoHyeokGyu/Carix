package com.carix.authservice.magiclink;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.AbstractIntegrationTest;
import com.carix.authservice.invitation.Invitation;
import com.carix.authservice.invitation.InvitationRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class MagicLinkControllerTest extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InvitationRepository invitationRepository;

	@Test
	void 초대된_이메일이면_발송_응답을_반환한다() throws Exception {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		invitationRepository.save(new Invitation(UUID.randomUUID(), "invited-a@example.com", "PENDING", now.plusDays(7), now));

		mockMvc.perform(post("/v1/auth/magic-link")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"invited-a@example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.sentAt").exists());
	}

	@Test
	void 초대되지_않은_이메일도_동일한_응답을_반환한다() throws Exception {
		mockMvc.perform(post("/v1/auth/magic-link")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"not-invited@example.com\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.sentAt").exists());
	}

	@Test
	void 빈_이메일이면_400을_반환한다() throws Exception {
		mockMvc.perform(post("/v1/auth/magic-link")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INPUT"));
	}
}
