package com.carix.authservice.session;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "auth", description = "세션 관리")
public class SessionController {

	private final SessionService sessionService;

	public SessionController(SessionService sessionService) {
		this.sessionService = sessionService;
	}

	@Operation(summary = "세션 검증",
			description = "Gateway 가 세션 토큰을 검증하고 사용자 ID를 조회하는 내부용 엔드포인트다.")
	@GetMapping("/v1/auth/session")
	public ResponseEntity<SessionValidationResponse> validate(
			@CookieValue(name = "session_token", required = false) String sessionToken) {
		if (sessionToken == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		return sessionService.findUserIdBySessionToken(sessionToken)
				.map(userId -> ResponseEntity.ok(new SessionValidationResponse(userId)))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	@Operation(summary = "로그아웃", description = "현재 세션만 폐기한다.")
	@PostMapping("/v1/auth/logout")
	public void logout(
			@CookieValue(name = "session_token", required = false) String sessionToken,
			HttpServletResponse httpResponse) {
		if (sessionToken != null) {
			sessionService.revoke(sessionToken);
		}
		clearCookie(httpResponse);
	}

	@Operation(summary = "전체 기기 로그아웃", description = "현재 세션의 사용자가 가진 모든 세션을 폐기한다.")
	@PostMapping("/v1/auth/logout-all")
	public void logoutAll(
			@CookieValue(name = "session_token", required = false) String sessionToken,
			HttpServletResponse httpResponse) {
		if (sessionToken != null) {
			sessionService.findUserIdBySessionToken(sessionToken)
					.ifPresent(sessionService::revokeAll);
		}
		clearCookie(httpResponse);
	}

	private void clearCookie(HttpServletResponse httpResponse) {
		ResponseCookie cookie = ResponseCookie.from("session_token", "")
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/api")
				.maxAge(Duration.ZERO)
				.build();
		httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
