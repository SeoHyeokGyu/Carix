package com.carix.authservice.account;

import com.carix.authservice.ratelimit.RateLimitService;
import com.carix.authservice.session.SessionCookies;
import com.carix.authservice.session.SessionService;
import com.carix.authservice.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 회원가입과 로그인. 둘 다 성공하면 세션 쿠키를 발급한다. */
@RestController
@Tag(name = "auth", description = "회원가입·로그인")
public class AccountController {

  private static final int REGISTER_IP_LIMIT_PER_HOUR = 20;
  private static final int LOGIN_EMAIL_LIMIT_PER_HOUR = 10;
  private static final int LOGIN_IP_LIMIT_PER_HOUR = 30;

  private final AccountService accountService;
  private final SessionService sessionService;
  private final RateLimitService rateLimitService;

  public AccountController(
      AccountService accountService,
      SessionService sessionService,
      RateLimitService rateLimitService) {
    this.accountService = accountService;
    this.sessionService = sessionService;
    this.rateLimitService = rateLimitService;
  }

  @Operation(
      summary = "회원가입",
      description = "가입 즉시 세션 쿠키를 발급한다. 이메일 소유는 증명하지 않는다 — 확인 메일을 보낼 경로가 없다.")
  @PostMapping("/v1/auth/register")
  public AuthResponse register(
      @Valid @RequestBody RegisterRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {

    // 이메일 중복 응답으로 주소 존재 여부가 드러나므로, 목록을 긁는 것을 IP 제한으로 막는다.
    ensureWithinLimit(
        rateLimitService.isExceeded(
            "register-ip", httpRequest.getRemoteAddr(), REGISTER_IP_LIMIT_PER_HOUR));

    User user = accountService.register(request.email(), request.password());
    SessionCookies.issue(httpResponse, sessionService.createSession(user.getId()));
    return AuthResponse.from(user);
  }

  @Operation(summary = "로그인", description = "없는 이메일과 비밀번호 불일치를 구분하지 않는다 (계정 열거 방지).")
  @PostMapping("/v1/auth/login")
  public AuthResponse login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {

    // 이메일 기준 제한이 핵심이다. IP 만 걸면 분산 대입에 뚫린다.
    // 두 검사를 짧은 회로(||)로 묶지 않는다 — 앞이 걸리면 뒤 카운터가 올라가지 않아 제한이 헐거워진다.
    boolean exceeded =
        rateLimitService.isExceeded(
                "login-email",
                request.email().trim().toLowerCase(Locale.ROOT),
                LOGIN_EMAIL_LIMIT_PER_HOUR)
            | rateLimitService.isExceeded(
                "login-ip", httpRequest.getRemoteAddr(), LOGIN_IP_LIMIT_PER_HOUR);
    ensureWithinLimit(exceeded);

    User user = accountService.authenticate(request.email(), request.password());
    SessionCookies.issue(httpResponse, sessionService.createSession(user.getId()));
    return AuthResponse.from(user);
  }

  private void ensureWithinLimit(boolean exceeded) {
    if (exceeded) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "요청이 많아 잠시 후 다시 시도해 주세요");
    }
  }
}
