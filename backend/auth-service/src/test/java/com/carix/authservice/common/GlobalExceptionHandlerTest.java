package com.carix.authservice.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.authservice.account.EmailTakenException;
import com.carix.authservice.account.InvalidCredentialsException;
import com.carix.authservice.account.PasswordTooLongException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void handlePasswordTooLong() {
    ResponseEntity<ErrorResponse> res =
        handler.handlePasswordTooLong(new PasswordTooLongException());
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(res.getBody().code()).isEqualTo("INVALID_INPUT");
  }

  @Test
  void handleEmailTaken() {
    ResponseEntity<ErrorResponse> res = handler.handleEmailTaken(new EmailTakenException());
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(res.getBody().code()).isEqualTo("EMAIL_TAKEN");
  }

  @Test
  void handleInvalidCredentials() {
    ResponseEntity<ErrorResponse> res =
        handler.handleInvalidCredentials(new InvalidCredentialsException());
    assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(res.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
  }
}
