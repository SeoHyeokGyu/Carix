package com.carix.pipeline.health;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데이터 상태 판정 기준. {@code cpiMaxLagMonths}·{@code peerMinSample} 은 core-api 의 {@code
 * CategoryCpiRateService.MAX_LAG_MONTHS}·{@code PeerGroupFallbackResolver.MIN_SAMPLE_SIZE} 와 같은
 * 값이어야 한다 — 바꾸면 두 곳을 함께 고친다.
 *
 * <p>NOTE: record가 아니라 가변 JavaBean을 사용하는 이유. Spring Cloud의 {@code
 * ConfigurationPropertiesRebinder.resetBeanToDefaults()} 가 무인자 생성자로 새 인스턴스를 만든 뒤 값을 다시 바인딩한다.
 * record 는 무인자 생성자가 없어 갱신 때 {@code BeanInstantiationException: wrong number of arguments} 로 깨진다.
 */
@ConfigurationProperties("carix.collector.health")
public class HealthThresholds {

  private int sampleTarget = 250;
  private double sampleWarnRatio = 0.8;
  private int cpiMaxLagMonths = 2;
  private int peerMinSample = 10;
  private int crawlStaleDays = 35;

  public int getSampleTarget() {
    return sampleTarget;
  }

  public void setSampleTarget(int value) {
    this.sampleTarget = value > 0 ? value : 250;
  }

  public double getSampleWarnRatio() {
    return sampleWarnRatio;
  }

  public void setSampleWarnRatio(double value) {
    this.sampleWarnRatio = value > 0 ? value : 0.8;
  }

  public int getCpiMaxLagMonths() {
    return cpiMaxLagMonths;
  }

  public void setCpiMaxLagMonths(int value) {
    this.cpiMaxLagMonths = value > 0 ? value : 2;
  }

  public int getPeerMinSample() {
    return peerMinSample;
  }

  public void setPeerMinSample(int value) {
    this.peerMinSample = value > 0 ? value : 10;
  }

  public int getCrawlStaleDays() {
    return crawlStaleDays;
  }

  public void setCrawlStaleDays(int value) {
    this.crawlStaleDays = value > 0 ? value : 35;
  }

  // Record-style accessors for backward compatibility
  public int sampleTarget() {
    return sampleTarget;
  }

  public double sampleWarnRatio() {
    return sampleWarnRatio;
  }

  public int cpiMaxLagMonths() {
    return cpiMaxLagMonths;
  }

  public int peerMinSample() {
    return peerMinSample;
  }

  public int crawlStaleDays() {
    return crawlStaleDays;
  }

  public static HealthThresholds defaults() {
    return new HealthThresholds();
  }
}
