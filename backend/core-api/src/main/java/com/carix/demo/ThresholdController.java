package com.carix.demo;

import com.carix.config.IndexThresholdProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Config Server 연동 확인용 엔드포인트.
 *
 * <p>@RefreshScope 는 필요 없다. ConfigurationPropertiesRebinder 가 주입된 프로퍼티 빈에 같은 인스턴스로 새 값을 다시 바인딩하므로,
 * 이 참조가 그대로여도 갱신된 값을 읽는다.
 *
 * <p>데모가 아니다 — [DEVELOPMENT.md] 가 Config Server 연동 확인 절차로 이 경로를 규정한다. 서버 {@code
 * config/spring/core-api.yml} 값이 실제로 주입됐는지 눈으로 보는 유일한 경로다. 옮기거나 지우려면 그 절차의 대체 수단을 먼저 정해야 한다.
 */
@RestController
@RequestMapping("/v1/demo")
@Tag(name = "demo", description = "Config Server 연동 확인 도구 (DEVELOPMENT.md)")
public class ThresholdController {

  private final IndexThresholdProperties properties;

  public ThresholdController(IndexThresholdProperties properties) {
    this.properties = properties;
  }

  @Operation(
      summary = "현재 적용 중인 지수 임계값",
      description = "Config Server 에서 읽은 값이다. 설정 커밋 후 /actuator/refresh 를 호출하면 바뀐다.")
  @GetMapping("/thresholds")
  public IndexThresholdProperties thresholds() {
    return properties;
  }
}
