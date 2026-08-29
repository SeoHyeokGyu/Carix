package com.carix.config;

import com.carix.common.usercontext.CurrentUserId;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서 메타데이터.
 *
 * <p>서버 URL은 선언하지 않는다. 컨텍스트 패스가 {@code /api}이고 엣지 Caddy가 그 경로를 그대로 넘기므로, springdoc이 요청에서 추론한 값이
 * 로컬·운영 모두에서 맞는다.
 */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Carix API",
            version = "v1",
            description =
                """
            커리어 구매력 지수 서비스의 내부 REST API.

            규격 정본은 저장소의 docs/specs/api-specification.md 다.
            현재는 배포 파이프라인 검증용 최소 스캐폴드이며 도메인 API는 아직 없다."""))
public class OpenApiConfig {

  static {
    // @CurrentUserId 는 게이트웨이 헤더에서 주입되는 값이라 호출자가 채울 파라미터가 아니다.
    // 등록하지 않으면 문서와 Swagger UI 에 존재하지 않는 입력란으로 나타난다.
    SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
  }
}
