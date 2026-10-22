package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthThresholdsTest {

  @Test
  @DisplayName("0 이하 값을 set 하면 기본값을 유지한다")
  void setter_ignoresZeroAndNegative() {
    HealthThresholds t = new HealthThresholds();
    t.setCpiMaxLagMonths(-5);

    assertThat(t.cpiMaxLagMonths()).isEqualTo(2);
  }
}
