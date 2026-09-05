package com.carix.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.gateway.session.UserIdHeaderStripGlobalFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.Ordered;

@SpringBootTest
class RouteConfigurationTest {

  @Autowired private RouteLocator routeLocator;

  @Test
  void 라우트_다섯개가_auth_coreHealth_collectorAdmin_core_web_순서로_로드된다() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();

    assertThat(routes)
        .extracting(Route::getId)
        .containsExactly("auth", "core-health", "collector-admin", "core", "web");
    assertThat(routes.get(0).getUri().toString()).isEqualTo("lb://auth-service");
    assertThat(routes.get(1).getUri().toString()).isEqualTo("lb://core-api");
    assertThat(routes.get(2).getUri().toString()).isEqualTo("lb://collector");
    assertThat(routes.get(3).getUri().toString()).isEqualTo("lb://core-api");
    assertThat(routes.get(4).getUri().toString()).isEqualTo("http://frontend-node:3000");
  }

  @Test
  void 헬스체크_라우트는_세션검증_필터를_달지_않는다() {
    // 배포 워크플로가 쿠키 없이 /api/health 를 호출한다. 여기 필터가 붙으면 플래그를 켜는 순간
    // 배포가 401 로 죽고, 롤백이 이미지 태그만 되돌리므로 다음 배포도 같은 자리에서 죽는다.
    List<Route> routes = routeLocator.getRoutes().collectList().block();

    assertThat(routeById(routes, "core-health").getFilters()).isEmpty();
    assertThat(routeById(routes, "core").getFilters()).isNotEmpty();
  }

  @Test
  void 인바운드_헤더_제거가_세션검증보다_먼저_돈다() {
    // UserIdHeaderStripGlobalFilter 는 HIGHEST_PRECEDENCE 다. 순서가 뒤집히면 세션 검증이 주입한
    // X-User-Id 를 그 뒤에 벗겨내 core-api 가 전부 401 이 된다.
    List<Route> routes = routeLocator.getRoutes().collectList().block();

    assertThat(routeById(routes, "core").getFilters())
        .allSatisfy(
            filter ->
                assertThat(((Ordered) filter).getOrder())
                    .isGreaterThan(new UserIdHeaderStripGlobalFilter().getOrder()));
  }

  private static Route routeById(List<Route> routes, String id) {
    return routes.stream()
        .filter(route -> id.equals(route.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("라우트를 찾지 못했다: " + id));
  }
}
