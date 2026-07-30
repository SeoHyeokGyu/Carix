package com.carix.demo;

import com.carix.config.IndexThresholdProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Config Server 연동 확인용 임시 엔드포인트.
 *
 * <p>TODO: (cjkang) 지수 계산이 구현되면 이 컨트롤러를 제거한다.
 */
@RestController
@RequestMapping("/v1/demo")
@RefreshScope
@Tag(name = "demo", description = "임시 확인용. 도메인 API 도입 시 제거한다")
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
