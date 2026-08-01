package com.carix.authservice.magiclink;

import com.carix.authservice.invitation.InvitationService;
import com.carix.authservice.ratelimit.RateLimitService;
import com.carix.authservice.session.SessionService;
import com.carix.authservice.user.User;
import com.carix.authservice.user.UserRepository;
import com.carix.authservice.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name = "auth", description = "초대·매직링크 로그인")
public class MagicLinkController {

	private static final int EMAIL_LIMIT_PER_HOUR = 5;
	private static final int IP_LIMIT_PER_HOUR = 20;

	private final RateLimitService rateLimitService;
	private final InvitationService invitationService;
	private final UserService userService;
	private final MagicLinkService magicLinkService;
	private final MagicLinkNotifier notifier;
	private final SessionService sessionService;
	private final UserRepository userRepository;

	public MagicLinkController(
			RateLimitService rateLimitService,
			InvitationService invitationService,
			UserService userService,
			MagicLinkService magicLinkService,
			MagicLinkNotifier notifier,
			SessionService sessionService,
			UserRepository userRepository) {
		this.rateLimitService = rateLimitService;
		this.invitationService = invitationService;
		this.userService = userService;
		this.magicLinkService = magicLinkService;
		this.notifier = notifier;
		this.sessionService = sessionService;
		this.userRepository = userRepository;
	}

	@Operation(summary = "매직링크 발송 요청",
			description = "초대되지 않은 이메일에도 동일한 응답을 반환한다 (계정 열거 방지).")
	@PostMapping("/v1/auth/magic-link")
	public MagicLinkResponse requestMagicLink(@Valid @RequestBody MagicLinkRequest request, HttpServletRequest httpRequest) {
		// InvitationService/UserService 도 동일하게 정규화한다 — 여기서 맞추면 그쪽 정규화는
		// 방어적 이중 처리가 되고, 안 맞추면 케이스만 다른 이메일이 별도 rate limit 버킷을 갖는다.
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		String ip = httpRequest.getRemoteAddr();

		boolean exceeded = rateLimitService.isExceeded("email", email, EMAIL_LIMIT_PER_HOUR)
				| rateLimitService.isExceeded("ip", ip, IP_LIMIT_PER_HOUR);
		if (exceeded) {
			return MagicLinkResponse.rateLimited();
		}

		if (invitationService.isInvited(email)) {
			User user = userService.findOrCreate(email);
			String rawToken = magicLinkService.issueToken(user.getId());
			notifier.send(email, rawToken);
		}

		return MagicLinkResponse.sent();
	}

	@Operation(summary = "매직링크 검증", description = "유효한 토큰이면 세션을 발급하고 쿠키에 담는다.")
	@PostMapping("/v1/auth/verify")
	public VerifyResponse verify(@Valid @RequestBody VerifyRequest request, HttpServletResponse httpResponse) {
		UUID userId = magicLinkService.verifyToken(request.token());
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));

		String sessionToken = sessionService.createSession(userId);
		ResponseCookie cookie = ResponseCookie.from("session_token", sessionToken)
				.httpOnly(true)
				.secure(true)
				.sameSite("Lax")
				.path("/api")
				.maxAge(Duration.ofDays(30))
				.build();
		httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

		return new VerifyResponse(user.getId(), user.getEmail(), user.isOnboardingComplete());
	}
}
