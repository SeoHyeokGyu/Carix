package com.carix.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 요청 검증 실패를 규격의 공통 에러 포맷으로 변환한다.
 *
 * <p>기본 동작(Spring의 ProblemDetail)은 {@code docs/specs/api-specification.md} §1.1 과
 * 형식이 다르다. 프런트엔드가 한 가지 형태만 다루도록 여기서 맞춘다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> details = new LinkedHashMap<>();
		for (var error : e.getBindingResult().getFieldErrors()) {
			details.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return ResponseEntity.badRequest()
				.body(ErrorResponse.of("INVALID_INPUT", "입력값 형식이 올바르지 않습니다.", details));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("INVALID_INPUT", e.getMessage(), null));
	}
}
