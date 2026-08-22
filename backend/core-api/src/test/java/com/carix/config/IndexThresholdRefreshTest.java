package com.carix.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code /actuator/refresh} 가 하는 일을 그대로 재현한다 — 환경을 바꾸고 {@link EnvironmentChangeEvent} 를 띄우면 {@code
 * ConfigurationPropertiesRebinder} 가 돈다.
 *
 * <p>이 검증이 없으면 record 로 되돌려도 빌드가 통과한다. record 는 무인자 생성자가 없어 리바인더의 {@code resetBeanToDefaults()} 에서
 * 깨지고, 그 사실은 런타임에만 드러난다.
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "carix.index.purchasing-power-warning-threshold=0.80",
      "carix.index.market-value-gap-warning-threshold=0.70"
    })
class IndexThresholdRefreshTest {

  @Autowired private ConfigurableApplicationContext context;

  @Autowired private IndexThresholdProperties properties;

  @Test
  void 환경이_바뀌면_재기동_없이_반영된다() {
    TestPropertyValues.of("carix.index.market-value-gap-warning-threshold=0.55").applyTo(context);

    context.publishEvent(
        new EnvironmentChangeEvent(Set.of("carix.index.market-value-gap-warning-threshold")));

    assertThat(properties.getMarketValueGapWarningThreshold()).isEqualTo(0.55);
    assertThat(properties.getPurchasingPowerWarningThreshold()).isEqualTo(0.80);
  }
}
