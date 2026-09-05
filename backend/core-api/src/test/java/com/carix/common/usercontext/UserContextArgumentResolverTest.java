package com.carix.common.usercontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;

class UserContextArgumentResolverTest {

  private final UserContextArgumentResolver resolver = new UserContextArgumentResolver();

  // Test dummy target method
  @SuppressWarnings("unused")
  private void dummyMethod(
      @CurrentUserId UUID validUserId,
      UUID noAnnotationUserId,
      @CurrentUserId String wrongTypeUserId) {}

  @Test
  @DisplayName("supportsParameter - @CurrentUserId 및 UUID 타입 파라미터 지원 여부 검증")
  void testSupportsParameter() throws Exception {
    Method method =
        UserContextArgumentResolverTest.class.getDeclaredMethod(
            "dummyMethod", UUID.class, UUID.class, String.class);

    MethodParameter validParam = new MethodParameter(method, 0);
    MethodParameter noAnnotationParam = new MethodParameter(method, 1);
    MethodParameter wrongTypeParam = new MethodParameter(method, 2);

    assertThat(resolver.supportsParameter(validParam)).isTrue();
    assertThat(resolver.supportsParameter(noAnnotationParam)).isFalse();
    assertThat(resolver.supportsParameter(wrongTypeParam)).isFalse();
  }

  @Test
  @DisplayName("resolveArgument - 유효한 X-User-Id 헤더가 있으면 UUID 반환")
  void testResolveArgumentSuccess() {
    UUID userId = UUID.randomUUID();
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getHeader("X-User-Id")).thenReturn(userId.toString());

    Object result = resolver.resolveArgument(null, null, webRequest, null);

    assertThat(result).isEqualTo(userId);
  }

  @Test
  @DisplayName("resolveArgument - X-User-Id 헤더가 null 이거나 공백이면 MissingUserContextException 발생")
  void testResolveArgumentMissingHeader() {
    NativeWebRequest webRequest = mock(NativeWebRequest.class);

    when(webRequest.getHeader("X-User-Id")).thenReturn(null);
    assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
        .isInstanceOf(MissingUserContextException.class);

    when(webRequest.getHeader("X-User-Id")).thenReturn("   ");
    assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
        .isInstanceOf(MissingUserContextException.class);
  }

  @Test
  @DisplayName("resolveArgument - X-User-Id 헤더가 잘못된 UUID 형식이면 MissingUserContextException 발생")
  void testResolveArgumentInvalidFormat() {
    NativeWebRequest webRequest = mock(NativeWebRequest.class);
    when(webRequest.getHeader("X-User-Id")).thenReturn("invalid-uuid-string");

    assertThatThrownBy(() -> resolver.resolveArgument(null, null, webRequest, null))
        .isInstanceOf(MissingUserContextException.class);
  }
}
