package com.carix.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "매직링크 요청 응답")
public record MagicLinkResponse(
    @Schema(example = "인증 링크가 발송되었습니다.") String message,
    Instant sentAt
) {

}
