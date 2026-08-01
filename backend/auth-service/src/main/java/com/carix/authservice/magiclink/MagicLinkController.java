package com.carix.authservice.magiclink;

import com.carix.authservice.invitation.InvitationService;
import com.carix.authservice.ratelimit.RateLimitService;
import com.carix.authservice.user.User;
import com.carix.authservice.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

	public MagicLinkController(
			RateLimitService rateLimitService,
			InvitationService invitationService,
			UserService userService,
			MagicLinkService magicLinkService,
			MagicLinkNotifier notifier) {
		this.rateLimitService = rateLimitService;
		this.invitationService = invitationService;
		this.userService = userService;
		this.magicLinkService = magicLinkService;
		this.notifier = notifier;
	}

	@Operation(summary = "매직링크 발송 요청",
			description = "초대되지 않은 이메일에도 동일한 응답을 반환한다 (계정 열거 방지).")
	@PostMapping("/v1/auth/magic-link")
	public MagicLinkResponse requestMagicLink(@Valid @RequestBody MagicLinkRequest request, HttpServletRequest httpRequest) {
		String email = request.email();
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
}
