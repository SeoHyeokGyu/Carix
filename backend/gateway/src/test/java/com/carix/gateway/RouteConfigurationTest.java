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

  @Test
  void 콜렉터_관리자_라우트가_필터를_가지고_lb_collector로_향한다() {
    List<Route> routes = routeLocator.getRoutes().collectList().block();
    Route collectorAdmin = routeById(routes, "collector-admin");

    assertThat(collectorAdmin.getUri().toString()).isEqualTo("lb://collector");
    assertThat(collectorAdmin.getFilters()).isNotEmpty();
  }

  private static Route routeById(List<Route> routes, String id) {
    return routes.stream()
        .filter(route -> id.equals(route.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("라우트를 찾지 못했다: " + id));
  }
}
