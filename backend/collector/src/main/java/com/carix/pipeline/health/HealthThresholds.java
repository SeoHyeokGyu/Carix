package com.carix.pipeline.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데이터 상태 판정 기준. {@code cpiMaxLagMonths}·{@code peerMinSample} 은 core-api 의 {@code
 * CategoryCpiRateService.MAX_LAG_MONTHS}·{@code PeerGroupFallbackResolver.MIN_SAMPLE_SIZE} 와 같은
 * 값이어야 한다 — 바꾸면 두 곳을 함께 고친다.
 */
@ConfigurationProperties("carix.collector.health")
public record HealthThresholds(
    int sampleTarget,
    double sampleWarnRatio,
    int cpiMaxLagMonths,
    int peerMinSample,
    int crawlStaleDays) {

  public HealthThresholds {
    sampleTarget = sampleTarget > 0 ? sampleTarget : 250;
    sampleWarnRatio = sampleWarnRatio > 0 ? sampleWarnRatio : 0.8;
    cpiMaxLagMonths = cpiMaxLagMonths > 0 ? cpiMaxLagMonths : 2;
    peerMinSample = peerMinSample > 0 ? peerMinSample : 10;
    crawlStaleDays = crawlStaleDays > 0 ? crawlStaleDays : 35;
  }

  public static HealthThresholds defaults() {
    return new HealthThresholds(0, 0, 0, 0, 0);
  }
}
