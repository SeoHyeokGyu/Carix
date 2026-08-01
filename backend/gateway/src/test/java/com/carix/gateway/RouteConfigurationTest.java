package com.carix.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest
class RouteConfigurationTest {

	@Autowired
	private RouteLocator routeLocator;

	@Test
	void 라우트_세개가_auth_core_web_순서로_로드된다() {
		List<Route> routes = routeLocator.getRoutes().collectList().block();

		assertThat(routes).extracting(Route::getId).containsExactly("auth", "core", "web");
		assertThat(routes.get(0).getUri().toString()).isEqualTo("lb://auth-service");
		assertThat(routes.get(1).getUri().toString()).isEqualTo("lb://core-api");
		assertThat(routes.get(2).getUri().toString()).isEqualTo("http://frontend-node:3000");
	}
}
