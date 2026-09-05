package com.carix.pipeline.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 수집기 관리자 파이프라인 API(/internal/pipeline/**) 접근 제어를 위한 Admin Key 검증 인터셉터.
 *
 * <p>모든 관리자 요청에 대해 요청 헤더 {@code X-Admin-Key}를 검증하며, 일치하지 않거나 누락된 경우 403 Forbidden을 반환합니다.
 */
@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

  private final String configuredAdminKey;

  public AdminKeyInterceptor(
      @Value("${carix.collector.admin-key:carix-admin-secret-dev-key}") String configuredAdminKey) {
    this.configuredAdminKey = configuredAdminKey;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    // OPTIONS 요청(CORS Preflight)은 통과
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    String providedKey = request.getHeader("X-Admin-Key");
    if (providedKey == null || !providedKey.equals(configuredAdminKey)) {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType("application/json;charset=UTF-8");
      response
          .getWriter()
          .write("{\"code\":\"FORBIDDEN\",\"message\":\"유효한 X-Admin-Key 헤더가 필요합니다.\"}");
      return false;
    }
    return true;
  }
}
