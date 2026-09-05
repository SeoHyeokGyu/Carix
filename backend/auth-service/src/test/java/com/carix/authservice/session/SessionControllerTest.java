package com.carix.authservice.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.user.User;
import com.carix.authservice.user.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SessionController.class)
class SessionControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-28T00:00:00Z");
  private static final String TOKEN = "a".repeat(64);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SessionService sessionService;
  @MockitoBean private UserRepository userRepository;

  private Cookie sessionCookie() {
    return new Cookie(SessionCookies.NAME, TOKEN);
  }

  @Test
  void 세션이_있으면_사용자_정보까지_돌려준다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID))
        .willReturn(
            Optional.of(new User(USER_ID, "beta@example.com", "$2a$10$h", "INCOME", NOW, NOW)));

    mockMvc
        .perform(get("/v1/auth/session").cookie(sessionCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.email").value("beta@example.com"))
        .andExpect(jsonPath("$.onboardingStep").value("INCOME"));
  }

  @Test
  void 쿠키가_없으면_401_이다() throws Exception {
    mockMvc.perform(get("/v1/auth/session")).andExpect(status().isUnauthorized());
  }

  @Test
  void 만료되었거나_없는_세션은_401_이다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.empty());

    mockMvc
        .perform(get("/v1/auth/session").cookie(sessionCookie()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 세션은_있는데_사용자_행이_사라졌으면_401_이다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

    mockMvc
        .perform(get("/v1/auth/session").cookie(sessionCookie()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 로그아웃은_세션을_지우고_쿠키를_만료시킨다() throws Exception {
    mockMvc
        .perform(post("/v1/auth/logout").cookie(sessionCookie()))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge(SessionCookies.NAME, 0));

    verify(sessionService).revoke(TOKEN);
  }

  @Test
  void 쿠키_없는_로그아웃은_세션을_건드리지_않는다() throws Exception {
    mockMvc.perform(post("/v1/auth/logout")).andExpect(status().isOk());

    verify(sessionService, never()).revoke(any());
  }

  @Test
  void 전체_로그아웃은_사용자의_모든_세션을_지운다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));

    mockMvc
        .perform(post("/v1/auth/logout-all").cookie(sessionCookie()))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge(SessionCookies.NAME, 0));

    verify(sessionService).revokeAll(USER_ID);
  }

  @Test
  void 쿠키_없는_전체_로그아웃은_세션을_건드리지_않는다() throws Exception {
    mockMvc.perform(post("/v1/auth/logout-all")).andExpect(status().isOk());
    verify(sessionService, never()).revokeAll(any());
  }

  @Test
  void 전체_로그아웃_시_세션은_있으나_사용자가_없으면_revokeAll을_호출하지_않는다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.empty());

    mockMvc
        .perform(post("/v1/auth/logout-all").cookie(sessionCookie()))
        .andExpect(status().isOk())
        .andExpect(cookie().maxAge(SessionCookies.NAME, 0));

    verify(sessionService, never()).revokeAll(any());
  }
}
