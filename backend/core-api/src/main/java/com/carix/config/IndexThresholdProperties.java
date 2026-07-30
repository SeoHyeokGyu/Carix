package com.carix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지수 경고 임계값. Config Server 가 제공하며 재배포 없이 바꿀 수 있다.
 *
 * <p>갱신하려면 설정 저장소에 커밋한 뒤 이 서비스의 {@code /actuator/refresh} 를 호출한다.
 * Spring Cloud Bus 를 쓰지 않으므로 자동 전파되지 않는다.
 */
@ConfigurationProperties(prefix = "carix.index")
public record IndexThresholdProperties(
		double purchasingPowerWarningThreshold,
		double marketValueGapWarningThreshold) {
}
