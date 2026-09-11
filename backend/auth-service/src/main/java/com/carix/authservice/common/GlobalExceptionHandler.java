package com.carix.authservice.common;

import java.util.LinkedHashMap;
import java.util.Map;
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

  @ExceptionHandler(com.carix.authservice.account.EmailTakenException.class)
  public ResponseEntity<ErrorResponse> handleEmailTaken(
      com.carix.authservice.account.EmailTakenException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
        .body(ErrorResponse.of("EMAIL_TAKEN", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.authservice.account.InvalidCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleInvalidCredentials(
      com.carix.authservice.account.InvalidCredentialsException e) {
    return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
        .body(ErrorResponse.of("INVALID_CREDENTIALS", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.authservice.account.PasswordTooLongException.class)
  public ResponseEntity<ErrorResponse> handlePasswordTooLong(
      com.carix.authservice.account.PasswordTooLongException e) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.of("INVALID_INPUT", e.getMessage(), null));
  }

  @ExceptionHandler(com.carix.authservice.user.OnboardingStepRegressionException.class)
  public ResponseEntity<ErrorResponse> handleOnboardingStepRegression(
      com.carix.authservice.user.OnboardingStepRegressionException e) {
    return ResponseEntity.badRequest()
        .body(ErrorResponse.of("ONBOARDING_STEP_REGRESSION", e.getMessage(), null));
  }
}
