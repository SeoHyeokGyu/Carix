package com.carix.authservice.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}
