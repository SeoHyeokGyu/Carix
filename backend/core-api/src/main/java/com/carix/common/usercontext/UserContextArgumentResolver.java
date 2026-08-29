package com.carix.common.usercontext;

import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUserId} 파라미터에 {@code X-User-Id} 헤더 값을 넣는다.
 *
 * <p>세션 검증은 게이트웨이가 하고 하류는 헤더를 신뢰한다(SECURITY §3.2). 게이트웨이는 인바운드 헤더를 벗긴 뒤 검증에 성공한 요청에만 다시 넣으므로, 여기
 * 도달한 값은 검증된 사용자다.
 *
 * <p>값이 없으면 실패시킨다. 대체 사용자로 떨어지면 남의 데이터를 조용히 내주게 된다.
 */
public class UserContextArgumentResolver implements HandlerMethodArgumentResolver {

  static final String HEADER = "X-User-Id";

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentUserId.class)
        && UUID.class.equals(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {

    String raw = webRequest.getHeader(HEADER);
    if (raw == null || raw.isBlank()) {
      throw new MissingUserContextException("인증이 필요합니다.");
    }

    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      // 형식이 깨진 값은 없는 것과 같이 다룬다. 어느 쪽인지 알려주면 탐색 단서가 된다.
      throw new MissingUserContextException("인증이 필요합니다.");
    }
  }
}
