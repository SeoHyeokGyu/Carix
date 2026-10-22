package com.carix.authservice.account;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.ratelimit.RateLimitService;
import com.carix.authservice.session.SessionService;
import com.carix.authservice.user.User;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AccountController.class)
class AccountControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-28T00:00:00Z");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AccountService accountService;
  @MockitoBean private SessionService sessionService;
  @MockitoBean private RateLimitService rateLimitService;

  private User registered(String step) {
    return new User(USER_ID, "beta@example.com", "$2a$10$hash", step, NOW, NOW);
  }

  @Test
  void 가입하면_세션_쿠키를_발급한다() throws Exception {
    given(accountService.register("beta@example.com", "password123"))
        .willReturn(registered("PROFILE"));
    given(sessionService.createSession(USER_ID)).willReturn("session-token");

    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"beta@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
        .andExpect(jsonPath("$.onboardingStep").value("PROFILE"))
        .andExpect(cookie().httpOnly("session_token", true))
        .andExpect(cookie().secure("session_token", true))
        .andExpect(header().string("Set-Cookie", Matchers.containsString("SameSite=Lax")));
  }

  @Test
  void 이메일이_중복이면_409_로_알려준다_숨기지_않는다() throws Exception {
    willThrow(new EmailTakenException()).given(accountService).register(anyString(), anyString());

    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"taken@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void 가입_IP_제한을_넘으면_429_이고_계정을_만들지_않는다() throws Exception {
    given(rateLimitService.isExceeded(eq("register-ip"), anyString(), anyInt())).willReturn(true);

    mockMvc
        .perform(
            post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"beta@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isTooManyRequests());

    verify(accountService, never()).register(anyString(), anyString());
  }

  @Test
  void 로그인_실패는_401_하나로_통일한다() throws Exception {
    willThrow(new InvalidCredentialsException())
        .given(accountService)
        .authenticate(anyString(), anyString());

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @Test
  void 로그인은_이메일과_IP_제한을_모두_센다_앞이_걸려도_뒤_카운터가_올라가야_한다() throws Exception {
    given(rateLimitService.isExceeded(eq("login-email"), anyString(), anyInt())).willReturn(true);

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"beta@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isTooManyRequests());

    verify(rateLimitService).isExceeded(eq("login-ip"), anyString(), anyInt());
    verify(accountService, never()).authenticate(anyString(), anyString());
  }

  @Test
  void 로그인_제한은_정규화한_이메일로_센다_케이스를_바꿔_우회할_수_없다() throws Exception {
    given(accountService.authenticate(anyString(), anyString())).willReturn(registered("PROFILE"));
    given(sessionService.createSession(USER_ID)).willReturn("session-token");

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  BETA@Example.COM \",\"password\":\"password123\"}"))
        .andExpect(status().isOk());

    verify(rateLimitService).isExceeded(eq("login-email"), eq("beta@example.com"), anyInt());
  }
}
