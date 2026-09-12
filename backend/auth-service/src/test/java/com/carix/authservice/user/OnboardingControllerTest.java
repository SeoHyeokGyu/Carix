package com.carix.authservice.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.session.SessionCookies;
import com.carix.authservice.session.SessionService;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OnboardingController.class)
class OnboardingControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-07T00:00:00Z");
  private static final String TOKEN = "a".repeat(64);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SessionService sessionService;
  @MockitoBean private UserRepository userRepository;

  private Cookie sessionCookie() {
    return new Cookie(SessionCookies.NAME, TOKEN);
  }

  private User user(String step) {
    return new User(USER_ID, "beta@example.com", "$2a$10$h", step, NOW, NOW);
  }

  @Test
  void 단계를_갱신하고_현재_단계를_돌려준다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("PROFILE")));

    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"INCOME\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStep").value("INCOME"))
        .andExpect(jsonPath("$.completed").value(false));

    then(userRepository).should().save(any(User.class));
  }

  @Test
  void COMPLETED_로_옮기면_완료로_표시한다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("REVIEW")));

    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));

    then(userRepository).should().save(any(User.class));
  }

  @Test
  void 쿠키가_없으면_401_이다() throws Exception {
    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"INCOME\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 만료되었거나_없는_세션은_401_이다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.empty());

    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"INCOME\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void 허용되지_않는_단계값은_400_이다() throws Exception {
    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"DONE\"}"))
        .andExpect(status().isBadRequest());
  }

  // FIX 1 (Critical, 최종 리뷰) — 서버는 클라이언트 가드(routeGuards.ts
  // isForwardStep)의 2차 방어선이다. 클라이언트 버그나 가드를 우회한 직접 호출로
  // 뒤로 가는 요청이 와도 users.onboarding_step 이 조용히 되돌아가면 안 된다.
  @Test
  void 뒤로_가는_단계_전환은_400_이고_저장하지_않는다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("REVIEW")));

    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"PROFILE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ONBOARDING_STEP_REGRESSION"));

    then(userRepository).should(never()).save(any(User.class));
  }

  // 같은 단계로의 재전환은 "뒤로"가 아니다 — 완료(COMPLETED) 후 재진입이 허용된
  // 검토 화면(guardOnboarding)을 다시 제출하는 정상 경로가 이 케이스를 실제로 탄다.
  // 막으면 멀쩡한 재제출이 400 을 받는다.
  @Test
  void 같은_단계로의_재전환은_허용한다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("COMPLETED")));

    mockMvc
        .perform(
            patch("/v1/auth/onboarding")
                .cookie(sessionCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentStep\":\"COMPLETED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.completed").value(true));

    then(userRepository).should().save(any(User.class));
  }

  @Test
  void 온보딩_재시작은_COMPLETED_상태인_사용자도_PROFILE_로_초기화한다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.of(USER_ID));
    given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("COMPLETED")));

    mockMvc
        .perform(post("/v1/auth/onboarding/reset").cookie(sessionCookie()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentStep").value("PROFILE"))
        .andExpect(jsonPath("$.completed").value(false));

    then(userRepository).should().save(any(User.class));
  }

  @Test
  void 온보딩_재시작_시_쿠키가_없으면_401_이다() throws Exception {
    mockMvc.perform(post("/v1/auth/onboarding/reset")).andExpect(status().isUnauthorized());
  }

  @Test
  void 온보딩_재시작_시_만료된_세션은_401_이다() throws Exception {
    given(sessionService.findUserIdBySessionToken(TOKEN)).willReturn(Optional.empty());

    mockMvc
        .perform(post("/v1/auth/onboarding/reset").cookie(sessionCookie()))
        .andExpect(status().isUnauthorized());
  }
}
