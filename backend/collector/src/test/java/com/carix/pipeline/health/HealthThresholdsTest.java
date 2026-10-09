package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthThresholdsTest {

  @Test
  @DisplayName("defaults() 는 기본값 인스턴스를 돌린다")
  void defaults_returnsDefaultValues() {
    HealthThresholds t = HealthThresholds.defaults();

    assertThat(t.cpiMaxLagMonths()).isEqualTo(2);
    assertThat(t.crawlStaleDays()).isEqualTo(35);
  }

  @Test
  @DisplayName("0 이하 값을 set 하면 기본값을 유지한다")
  void setter_ignoresZeroAndNegative() {
    HealthThresholds t = new HealthThresholds();
    t.setCpiMaxLagMonths(-5);

    assertThat(t.cpiMaxLagMonths()).isEqualTo(2);
  }

  @Test
  @DisplayName("양수 값을 set 하면 저장된다")
  void setter_acceptsPositiveValues() {
    HealthThresholds t = new HealthThresholds();
    t.setCpiMaxLagMonths(3);
    t.setCrawlStaleDays(40);

    assertThat(t.cpiMaxLagMonths()).isEqualTo(3);
    assertThat(t.crawlStaleDays()).isEqualTo(40);
  }
}
