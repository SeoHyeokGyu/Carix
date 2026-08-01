package com.carix.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/**
 * 4xx·5xx 공통 에러 응답.
 *
 * <p>형식은 {@code docs/specs/api-specification.md} §1.1 을 따른다.
 *
 * @param code      기계 판독용 오류 코드
 * @param message   사용자에게 보여줄 메시지
 * @param timestamp 발생 시각 (UTC)
 * @param details   필드별 상세. 없으면 null
 */
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
