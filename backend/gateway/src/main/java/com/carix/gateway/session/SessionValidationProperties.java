package com.carix.gateway.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 세션 검증 필터 기능 플래그. Config Server 가 제공하며(config/spring/gateway.yml) 재배포 없이 /actuator/refresh 로 바꿀 수
 * 있다.
 *
 * <p>record 가 아니라 가변 JavaBean 인 이유는 IndexThresholdProperties(core-api) 와 같다 —
 * ConfigurationPropertiesRebinder 가 무인자 생성자로 갱신하기 때문이다.
 *
 * <p>기본값 false. true 로 켜면 세션 쿠키 없는 core 라우트 호출이 전부 401 이 되므로 팀 조율 후 켠다. 켜고 끄는 절차는
 * config/spring/gateway.yml 주석에 있다.
 */
@ConfigurationProperties(prefix = "carix.gateway.session-validation")
public class SessionValidationProperties {

  private boolean enabled;

  public boolean isEnabled() {
    return this.enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
