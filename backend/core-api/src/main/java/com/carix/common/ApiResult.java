package com.carix.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "공통 API 응답")
public record ApiResult<T>(
    @Schema(description = "성공 여부", example = "true") boolean success,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "응답 데이터") T data,
    @Schema(description = "응답 메시지", example = "요청이 성공적으로 처리되었습니다.") String message,
    Instant timestamp) {

  public static <T> ApiResult<T> success(T data) {
    return new ApiResult<>(true, data, "요청이 성공적으로 처리되었습니다.", Instant.now());
  }

  public static <T> ApiResult<T> success(T data, String message) {
    return new ApiResult<>(true, data, message, Instant.now());
  }

  public static <T> ApiResult<T> fail(String message) {
    return new ApiResult<>(false, null, message, Instant.now());
  }
}
