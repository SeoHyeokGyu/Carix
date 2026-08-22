package com.carix.authservice;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** auth-service 애플리케이션의 헬스체크 엔드포인트를 제공하는 컨트롤러. */
@RestController
@Tag(name = "health", description = "배포 상태 확인")
public class HealthController {

  @Operation(summary = "헬스체크", description = "애플리케이션이 요청을 받을 수 있으면 UP을 반환한다.")
  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "UP");
  }
}
