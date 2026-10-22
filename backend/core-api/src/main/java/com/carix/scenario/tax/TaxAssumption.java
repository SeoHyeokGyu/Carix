package com.carix.scenario.tax;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 세후 연봉 추정에 쓰는 근로자 부담 요율과 기준 시점.
 *
 * <p>공시값이지만 해마다 바뀌므로 코드 상수가 아니라 설정으로 두고 기준 시점({@code ratesAsOf})을 함께 들고 다닌다. 소득공제·세율 구간표는 법령에 고정된
 * 표라 {@link TakeHomeEstimator} 에 둔다.
 *
 * @param ratesAsOf 요율 기준 시점. 화면 라벨에 그대로 실린다
 * @param nationalPensionRate 국민연금 근로자 부담률
 * @param pensionIncomeFloor 국민연금 기준소득월액 하한(원, 월)
 * @param pensionIncomeCap 국민연금 기준소득월액 상한(원, 월)
 * @param healthRate 건강보험 근로자 부담률
 * @param longTermCareRatio 장기요양보험료 / 건강보험료
 * @param employmentRate 고용보험 근로자 부담률
 */
@ConfigurationProperties(prefix = "carix.tax")
public record TaxAssumption(
    String ratesAsOf,
    double nationalPensionRate,
    long pensionIncomeFloor,
    long pensionIncomeCap,
    double healthRate,
    double longTermCareRatio,
    double employmentRate) {}
