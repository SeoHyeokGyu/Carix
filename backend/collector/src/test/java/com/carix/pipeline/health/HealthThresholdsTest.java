package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthThresholdsTest {

  @Test
  @DisplayName("defaults() 는 기본값 인스턴스를 돌린다")
  void defaults_returnsDefaultValues() {
    HealthThresholds t = HealthThresholds.defaults();

    assertThat(t.sampleTarget()).isEqualTo(250);
    assertThat(t.sampleWarnRatio()).isEqualTo(0.8);
    assertThat(t.cpiMaxLagMonths()).isEqualTo(2);
    assertThat(t.peerMinSample()).isEqualTo(10);
    assertThat(t.crawlStaleDays()).isEqualTo(35);
  }

  @Test
  @DisplayName("0 이하 값을 set 하면 기본값을 유지한다")
  void setter_ignoresZeroAndNegative() {
    HealthThresholds t = new HealthThresholds();
    t.setSampleTarget(0);
    t.setSampleWarnRatio(-1.0);
    t.setCpiMaxLagMonths(-5);

    assertThat(t.sampleTarget()).isEqualTo(250);
    assertThat(t.sampleWarnRatio()).isEqualTo(0.8);
    assertThat(t.cpiMaxLagMonths()).isEqualTo(2);
  }

  @Test
  @DisplayName("양수 값을 set 하면 저장된다")
  void setter_acceptsPositiveValues() {
    HealthThresholds t = new HealthThresholds();
    t.setSampleTarget(100);
    t.setSampleWarnRatio(0.5);
    t.setCpiMaxLagMonths(3);
    t.setPeerMinSample(20);
    t.setCrawlStaleDays(40);

    assertThat(t.sampleTarget()).isEqualTo(100);
    assertThat(t.sampleWarnRatio()).isEqualTo(0.5);
    assertThat(t.cpiMaxLagMonths()).isEqualTo(3);
    assertThat(t.peerMinSample()).isEqualTo(20);
    assertThat(t.crawlStaleDays()).isEqualTo(40);
  }
}
