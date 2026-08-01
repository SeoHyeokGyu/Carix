package com.carix.authservice.magiclink;

import java.time.OffsetDateTime;

public record MagicLinkResponse(String message, OffsetDateTime sentAt) {

	public static MagicLinkResponse sent() {
		return new MagicLinkResponse("인증 링크가 발송되었습니다.", OffsetDateTime.now());
	}

	public static MagicLinkResponse rateLimited() {
		return new MagicLinkResponse("요청이 많아 잠시 후 다시 시도해 주세요", OffsetDateTime.now());
	}
}
