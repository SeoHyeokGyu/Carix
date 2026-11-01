package com.carix.authservice.account;

import static org.mockito.ArgumentMatchers.any;
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
import com.carix.authservice.session.SessionLifetime;
import com.carix.authservice.session.SessionService;
import com.carix.authservice.user.User;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Optional;
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

  private static final UUID GUEST_ID = UUID.randomUUID();
  private static final String TOKEN = "a".repeat(64);

  private User registered(String step) {
    return new User(USER_ID, "beta@example.com", "$2a$10$hash", step, NOW, NOW);
  }

  @Test
  void 가입하면_세션_쿠키를_발급한다() throws Exception {
    given(accountService.register("beta@example.com", "password123", Optional.empty()))
        .willReturn(new RegistrationResult(registered("PROFILE"), false));
    given(sessionService.createSession(USER_ID, SessionLifetime.ACCOUNT))
        .willReturn("session-token");

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
        .andExpect(cookie().maxAge("session_token", 2_592_000))
        .andExpect(header().string("Set-Cookie", Matchers.containsString("SameSite=Lax")));
  }

  @Test
  void 이메일이_중복이면_409_로_알려준다_숨기지_않는다() throws Exception {
    willThrow(new EmailTakenException())
        .given(accountService)
        .register(anyString(), anyString(), any());

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

    verify(accountService, never()).register(anyString(), anyString(), any());
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
    given(sessionService.createSession(USER_ID, SessionLifetime.ACCOUNT))
        .willReturn("session-token");

    mockMvc
        .perform(
            post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  BETA@Example.COM \",\"password\":\"password123\"}"))
        .andExpect(status().isOk());

    verify(rateLimitService).isExceeded(eq("login-email"), eq("beta@example.com"), anyInt());
  }

  @Test
  void 게스트를_시작하면_24시간_쿠키를_발급한다() throws Exception {
    given(accountService.createGuest()).willReturn(User.guest(GUEST_ID, NOW));
    given(sessionService.createSession(GUEST_ID, SessionLifetime.GUEST)).willReturn("guest-token");

    mockMvc
        .perform(post("/v1/auth/guest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(GUEST_ID.toString()))
        .andExpect(jsonPath("$.guest").value(true))
        .andExpect(jsonPath("$.email").isEmpty())
        .andExpect(cookie().maxAge("session_token", 86_400));
  }

  @Test
  void 유효한_세션이_있으면_새_게스트를_만들지_않는다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(GUEST_ID));
    given(accountService.findUser(GUEST_ID)).willReturn(Optional.of(User.guest(GUEST_ID, NOW)));

    mockMvc
        .perform(post("/v1/auth/guest").cookie(new Cookie("session_token", TOKEN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(GUEST_ID.toString()))
        .andExpect(header().doesNotExist("Set-Cookie"));

    verify(accountService, never()).createGuest();
    verify(rateLimitService, never()).isExceeded(anyString(), anyString(), anyInt());
  }

  @Test
  void 게스트_생성이_제한을_넘으면_429() throws Exception {
    given(rateLimitService.isExceeded(eq("guest-ip"), anyString(), eq(20))).willReturn(true);

    mockMvc.perform(post("/v1/auth/guest")).andExpect(status().isTooManyRequests());

    verify(accountService, never()).createGuest();
  }

  @Test
  void 게스트_세션으로_가입하면_같은_id로_30일_쿠키를_준다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(GUEST_ID));
    User converted = new User(GUEST_ID, "beta@example.com", "$2a$10$hash", "PROFILE", NOW, NOW);
    given(accountService.register("beta@example.com", "password123", Optional.of(GUEST_ID)))
        .willReturn(new RegistrationResult(converted, true));
    given(sessionService.createSession(GUEST_ID, SessionLifetime.ACCOUNT)).willReturn("new-token");

    mockMvc
        .perform(
            post("/v1/auth/register")
                .cookie(new Cookie("session_token", TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"beta@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(GUEST_ID.toString()))
        .andExpect(jsonPath("$.guest").value(false))
        .andExpect(cookie().value("session_token", "new-token"))
        .andExpect(cookie().maxAge("session_token", 2_592_000));
  }

  @Test
  void 정식_사용자_세션으로_가입해도_기존_세션은_폐기하지_않는다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(accountService.register(anyString(), anyString(), eq(Optional.of(USER_ID))))
        .willReturn(
            new RegistrationResult(
                new User(UUID.randomUUID(), "n@example.com", "$2a$10$h", "PROFILE", NOW, NOW),
                false));
    given(sessionService.createSession(any(), eq(SessionLifetime.ACCOUNT))).willReturn("t");

    mockMvc
        .perform(
            post("/v1/auth/register")
                .cookie(new Cookie("session_token", TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"n@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk());

    verify(sessionService, never()).revokeAll(any());
  }

  @Test
  void 게스트_세션으로_다른_계정에_로그인하면_게스트_세션을_폐기한다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(GUEST_ID));
    given(accountService.findGuest(GUEST_ID)).willReturn(Optional.of(User.guest(GUEST_ID, NOW)));
    given(accountService.authenticate(anyString(), anyString())).willReturn(registered("PROFILE"));
    given(sessionService.createSession(USER_ID, SessionLifetime.ACCOUNT))
        .willReturn("session-token");

    mockMvc
        .perform(
            post("/v1/auth/login")
                .cookie(new Cookie("session_token", TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"beta@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk());

    verify(sessionService).revokeAll(GUEST_ID);
  }

  @Test
  void 정식_세션으로_다시_로그인해도_기존_세션은_폐기하지_않는다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(accountService.findGuest(USER_ID)).willReturn(Optional.empty());
    given(accountService.authenticate(anyString(), anyString())).willReturn(registered("PROFILE"));
    given(sessionService.createSession(USER_ID, SessionLifetime.ACCOUNT))
        .willReturn("session-token");

    mockMvc
        .perform(
            post("/v1/auth/login")
                .cookie(new Cookie("session_token", TOKEN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"beta@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk());

    verify(sessionService, never()).revokeAll(any());
  }
}
