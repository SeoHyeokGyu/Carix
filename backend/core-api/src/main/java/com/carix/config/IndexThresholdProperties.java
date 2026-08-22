package com.carix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지수 경고 임계값. Config Server 가 제공하며 재배포 없이 바꿀 수 있다.
 *
 * <p>갱신하려면 설정 저장소에 커밋한 뒤 이 서비스의 {@code /actuator/refresh} 를 호출한다. Spring Cloud Bus 를 쓰지 않으므로 자동
 * 전파되지 않는다.
 *
 * <p>NOTE: (cjkang) record 가 아니라 가변 JavaBean 인 이유. Spring Cloud 의 {@code
 * ConfigurationPropertiesRebinder.resetBeanToDefaults()} 가 무인자 생성자로 새 인스턴스를 만든 뒤 값을 다시 바인딩한다.
 * record 는 무인자 생성자가 없어 갱신 때 {@code BeanInstantiationException: wrong number of arguments: 0
 * expected: 2} 로 깨진다 (spring-cloud-context 5.0.2, 2026-07-30 실측). 리바인더는 같은 인스턴스에 값을 다시 넣으므로 이 빈을
 * 주입받은 쪽은 참조를 그대로 두고도 새 값을 본다.
 */
@ConfigurationProperties(prefix = "carix.index")
public class IndexThresholdProperties {

  private double purchasingPowerWarningThreshold;

  private double marketValueGapWarningThreshold;

  public double getPurchasingPowerWarningThreshold() {
    return this.purchasingPowerWarningThreshold;
  }

  public void setPurchasingPowerWarningThreshold(double purchasingPowerWarningThreshold) {
    this.purchasingPowerWarningThreshold = purchasingPowerWarningThreshold;
  }

  public double getMarketValueGapWarningThreshold() {
    return this.marketValueGapWarningThreshold;
  }

  public void setMarketValueGapWarningThreshold(double marketValueGapWarningThreshold) {
    this.marketValueGapWarningThreshold = marketValueGapWarningThreshold;
  }
}
