package com.carix.authservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /internal/**} 을 공유 시크릿으로 가린다. 게이트웨이가 이 경로를 라우팅하지 않아 외부에서는 이미 도달할 수 없고, 이 필터는 클러스터 내부 호출에
 * 대한 방어다.
 *
 * <p>키가 설정돼 있지 않으면 전부 거부한다(fail closed). 설정 누락이 곧 무인증 개방이 되면 안 된다.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Internal-Api-Key";
  private static final String PATH_PREFIX = "/internal/";

  private final String configuredKey;

  public InternalApiKeyFilter(@Value("${carix.auth.internal-api-key:}") String configuredKey) {
    this.configuredKey = configuredKey;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // getServletPath() 를 쓰지 않는다 — 컨텍스트 패스가 /api 라 배포 환경과 MockMvc 에서 값이 달라진다.
    String path = request.getRequestURI().substring(request.getContextPath().length());
    return !path.startsWith(PATH_PREFIX);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    if (!isAuthorized(request.getHeader(HEADER))) {
      // 키 미설정과 키 불일치를 같은 응답으로 돌려준다 — 설정 상태를 밖에서 알아낼 수 없게 한다.
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"내부 경로 접근 권한이 없습니다.\"}");
      return;
    }

    chain.doFilter(request, response);
  }

  private boolean isAuthorized(String presentedKey) {
    if (configuredKey == null || configuredKey.isBlank() || presentedKey == null) {
      return false;
    }
    return MessageDigest.isEqual(
        configuredKey.getBytes(StandardCharsets.UTF_8),
        presentedKey.getBytes(StandardCharsets.UTF_8));
  }
}
