package com.carix.authservice.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
		@Schema(example = "INVALID_INPUT") String code,
		@Schema(example = "입력값 형식이 올바르지 않습니다.") String message,
		Instant timestamp,
		@Schema(example = "{\"message\": \"공백일 수 없습니다\"}") Map<String, String> details) {

	public static ErrorResponse of(String code, String message, Map<String, String> details) {
		return new ErrorResponse(code, message, Instant.now(), details);
	}
}
