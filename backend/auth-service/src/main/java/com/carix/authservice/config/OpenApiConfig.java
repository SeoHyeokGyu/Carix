package com.carix.authservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
		info = @Info(
				title = "Carix Auth Service API",
				version = "v1",
				description = """
						초대·매직링크 로그인, 세션 관리 API.

						규격 정본은 저장소의 docs/specs/api-specification.md §2 다."""))
public class OpenApiConfig {
}
