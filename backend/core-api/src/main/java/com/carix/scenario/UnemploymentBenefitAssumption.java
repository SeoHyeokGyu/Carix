package com.carix.scenario;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 실업급여 계산에 쓰는 가정치 (도메인 정의서 §4.1).
 *
 * <p>상·하한은 법정 공시값이 아니라 시뮬레이션 가정치다. 명세가 <b>설정 파일에 기준 시점을 기록</b>하고 화면에 `가정 기반 추정` 라벨을 붙이라고 요구하므로 값과
 * 시점을 함께 들고 다닌다 — 코드에 상수로 박으면 공시 기준이 바뀌었을 때 무엇이 언제 기준이었는지 남지 않는다.
 *
 * @param dailyUpperLimit 1일 구직급여 상한액(원)
 * @param dailyLowerLimit 1일 구직급여 하한액(원). 최저임금의 80% 수준
 * @param assumptionAsOf 이 가정치의 기준 시점. 화면 라벨에 그대로 실린다
 */
@ConfigurationProperties(prefix = "carix.scenario.unemployment")
public record UnemploymentBenefitAssumption(
    long dailyUpperLimit, long dailyLowerLimit, String assumptionAsOf) {}
