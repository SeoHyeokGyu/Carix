package com.carix.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalApiKeyFilterTest {

  private static final String INTERNAL_PATH = "/internal/invitations";

  @Test
  void 키가_설정되지_않으면_올바른_헤더가_와도_거부한다() throws Exception {
    MockHttpServletResponse response = execute(new InternalApiKeyFilter(""), INTERNAL_PATH, "");

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void 내부_경로가_아니면_검사하지_않는다() throws Exception {
    FilterChain chain = Mockito.mock(FilterChain.class);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/auth/magic-link");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new InternalApiKeyFilter("some-key").doFilter(request, response, chain);

    Mockito.verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void 컨텍스트_패스가_붙어도_내부_경로를_알아본다() throws Exception {
    FilterChain chain = Mockito.mock(FilterChain.class);
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api" + INTERNAL_PATH);
    request.setContextPath("/api");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new InternalApiKeyFilter("some-key").doFilter(request, response, chain);

    Mockito.verifyNoInteractions(chain);
    assertThat(response.getStatus()).isEqualTo(403);
  }

  private MockHttpServletResponse execute(InternalApiKeyFilter filter, String path, String key)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.addHeader(InternalApiKeyFilter.HEADER, key);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, Mockito.mock(FilterChain.class));
    return response;
  }
}
