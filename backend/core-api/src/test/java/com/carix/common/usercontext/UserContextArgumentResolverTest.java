package com.carix.common.usercontext;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
