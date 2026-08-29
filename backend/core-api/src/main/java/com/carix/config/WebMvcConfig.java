package com.carix.config;

import com.carix.common.usercontext.UserContextArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 컨트롤러 파라미터 확장 등록.
 *
 * <p>{@code @WebMvcTest} 슬라이스는 이 설정을 자동으로 끌어오지 않는다. {@link
 * com.carix.common.usercontext.CurrentUserId} 를 쓰는 컨트롤러의 슬라이스 테스트는
 * {@code @Import(WebMvcConfig.class)} 를 붙여야 한다 — 빠뜨리면 파라미터를 해석하지 못한다는 모호한 오류가 난다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new UserContextArgumentResolver());
  }
}
