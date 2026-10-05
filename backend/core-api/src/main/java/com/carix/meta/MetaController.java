package com.carix.meta;

import com.carix.snapshot.DecisionSignalEngine;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서비스 메타 정보. collector 의 관리자 데이터 상태 점검이 사용자 스냅샷 규칙 버전을 현재 엔진과 비교하려고 서비스 간 호출로 읽는다. 규칙 버전은 민감 정보가
 * 아니라 사용자 컨텍스트를 요구하지 않는다.
 */
@RestController
@RequestMapping("/v1/meta")
public class MetaController {

  @GetMapping("/versions")
  public Map<String, String> versions() {
    return Map.of("ruleVersion", DecisionSignalEngine.RULE_VERSION);
  }
}
