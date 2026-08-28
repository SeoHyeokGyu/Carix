package com.carix.authservice.magiclink;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.invitation.InvitationService;
import com.carix.authservice.ratelimit.RateLimitService;
import com.carix.authservice.session.SessionService;
import com.carix.authservice.user.User;
import com.carix.authservice.user.UserRepository;
import com.carix.authservice.user.UserService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MagicLinkController.class)
class MagicLinkControllerVerifyTest {

  private static final UUID USER_ID = UUID.fromString("6a97e7d2-66da-4fbe-b033-7fa4072eaac1");
  private static final String TOKEN = "a".repeat(64);
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-28T00:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RateLimitService rateLimitService;
  @MockitoBean private InvitationService invitationService;
  @MockitoBean private UserService userService;
  @MockitoBean private MagicLinkService magicLinkService;
  @MockitoBean private MagicLinkNotifier notifier;
  @MockitoBean private SessionService sessionService;
  @MockitoBean private UserRepository userRepository;

  @Test
  void 토큰만으로_검증하고_세션_쿠키를_내린다() throws Exception {
    given(magicLinkService.verifyToken(TOKEN)).willReturn(USER_ID);
    given(userRepository.findById(USER_ID))
        .willReturn(Optional.of(new User(USER_ID, "beta@example.com", "PROFILE", NOW, NOW)));
    given(sessionService.createSession(USER_ID)).willReturn("session-token-value");

    mockMvc
        .perform(
            post("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.isOnboardingComplete").value(false))
        .andExpect(cookie().httpOnly("session_token", true))
        .andExpect(cookie().secure("session_token", true))
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Lax")));
  }

  @Test
  void 이메일을_요구하지_않는다_기기가_다를_수_있기_때문이다() throws Exception {
    given(magicLinkService.verifyToken(TOKEN)).willReturn(USER_ID);
    given(userRepository.findById(USER_ID))
        .willReturn(Optional.of(new User(USER_ID, "beta@example.com", "COMPLETED", NOW, NOW)));
    given(sessionService.createSession(USER_ID)).willReturn("session-token-value");

    // 이메일 필드가 아예 없는 본문이 200 이어야 한다.
    mockMvc
        .perform(
            post("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isOnboardingComplete").value(true));
  }

  @Test
  void 토큰이_비면_400이다() throws Exception {
    mockMvc
        .perform(
            post("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void 만료되었거나_이미_쓴_토큰은_400이다() throws Exception {
    willThrow(new MagicLinkInvalidException()).given(magicLinkService).verifyToken(anyString());

    mockMvc
        .perform(
            post("/v1/auth/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MAGIC_LINK_EXPIRED"));
  }
}
