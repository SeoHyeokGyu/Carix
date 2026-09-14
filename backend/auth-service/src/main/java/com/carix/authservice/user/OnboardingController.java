package com.carix.authservice.user;

import com.carix.authservice.session.SessionCookies;
import com.carix.authservice.session.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 온보딩 단계 갱신 및 재시작.
 *
 * <p>{@code users.onboarding_step} 은 auth-service 소유라 갱신도 여기서 한다. core-api 에 있던 같은 이름의 엔드포인트는 아무것도
 * 쓰지 않는 스텁이었고 철거했다 (개발 계획 §4 MVP 범위 판별).
 */
@RestController
@Tag(name = "auth", description = "세션 관리")
public class OnboardingController {

  private final SessionService sessionService;
  private final UserRepository userRepository;

  public OnboardingController(SessionService sessionService, UserRepository userRepository) {
    this.sessionService = sessionService;
    this.userRepository = userRepository;
  }

  @Operation(summary = "온보딩 단계 갱신", description = "세션 쿠키의 사용자에 대해 온보딩 진행 단계를 옮긴다.")
  @PatchMapping("/v1/auth/onboarding")
  public ResponseEntity<OnboardingUpdateResponse> update(
      @CookieValue(name = SessionCookies.NAME, required = false) String sessionToken,
      @Valid @RequestBody OnboardingUpdateRequest request) {

    if (sessionToken == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return sessionService
        .findUserIdBySessionToken(sessionToken)
        .flatMap(userRepository::findById)
        .map(
            user -> {
              user.changeOnboardingStep(request.currentStep(), OffsetDateTime.now(ZoneOffset.UTC));
              userRepository.save(user);
              return ResponseEntity.ok(OnboardingUpdateResponse.from(user));
            })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }

  @Operation(summary = "온보딩 재시작", description = "온보딩을 1단계(PROFILE)부터 다시 시작하도록 초기화한다.")
  @PostMapping("/v1/auth/onboarding/reset")
  public ResponseEntity<OnboardingUpdateResponse> reset(
      @CookieValue(name = SessionCookies.NAME, required = false) String sessionToken) {

    if (sessionToken == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    return sessionService
        .findUserIdBySessionToken(sessionToken)
        .flatMap(userRepository::findById)
        .map(
            user -> {
              user.resetOnboarding(OffsetDateTime.now(ZoneOffset.UTC));
              userRepository.save(user);
              return ResponseEntity.ok(OnboardingUpdateResponse.from(user));
            })
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }
}
