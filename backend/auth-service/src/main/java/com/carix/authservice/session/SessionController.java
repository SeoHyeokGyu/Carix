package com.carix.authservice.session;

import com.carix.authservice.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "auth", description = "세션 관리")
public class SessionController {

  private final SessionService sessionService;
  private final UserRepository userRepository;

  public SessionController(SessionService sessionService, UserRepository userRepository) {
    this.sessionService = sessionService;
    this.userRepository = userRepository;
  }

  @Operation(summary = "세션 조회", description = "게이트웨이 세션 검증 필터와 프런트엔드가 함께 쓴다. 게이트웨이는 userId 만 읽는다.")
  @GetMapping("/v1/auth/session")
  public ResponseEntity<SessionValidationResponse> validate(
      @CookieValue(name = SessionCookies.NAME, required = false) String sessionToken) {
    if (sessionToken == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    // 세션 조회와 사용자 조회가 각각 인덱스 단건 조회다. 게이트웨이는 이 응답을 캐시하므로
    // 요청마다 두 번씩 도는 것은 아니다.
    return sessionService
        .findUserIdBySessionToken(sessionToken)
        .flatMap(userRepository::findById)
        .map(
            user ->
                ResponseEntity.ok(
                    new SessionValidationResponse(
                        user.getId(), user.getEmail(), user.getOnboardingStep(), user.isGuest())))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
  }

  @Operation(summary = "로그아웃", description = "현재 세션만 폐기한다.")
  @PostMapping("/v1/auth/logout")
  public void logout(
      @CookieValue(name = SessionCookies.NAME, required = false) String sessionToken,
      HttpServletResponse httpResponse) {
    if (sessionToken != null) {
      sessionService.revoke(sessionToken);
    }
    clearCookie(httpResponse);
  }

  @Operation(summary = "전체 기기 로그아웃", description = "현재 세션의 사용자가 가진 모든 세션을 폐기한다.")
  @PostMapping("/v1/auth/logout-all")
  public void logoutAll(
      @CookieValue(name = SessionCookies.NAME, required = false) String sessionToken,
      HttpServletResponse httpResponse) {
    if (sessionToken != null) {
      sessionService.findUserIdBySessionToken(sessionToken).ifPresent(sessionService::revokeAll);
    }
    clearCookie(httpResponse);
  }

  private void clearCookie(HttpServletResponse httpResponse) {
    SessionCookies.clear(httpResponse);
  }
}
