package com.carix.scenario.inflation;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 앞으로 12개월 물가 가정의 기준이 되는 한국은행 전망치.
 *
 * <p>전망치는 보고서가 나올 때마다 바뀌므로 코드 상수가 아니라 설정으로 두고 기준 시점·출처를 함께 들고 다닌다.
 *
 * @param forecastPercent 다음 연도 소비자물가 상승률 전망(% 단위, 2.3 = 2.3%)
 * @param asOf 전망 발표월 {@code yyyy-MM}. 화면 라벨에 그대로 실린다
 * @param source 전망 출처 이름
 */
@ConfigurationProperties(prefix = "carix.inflation")
public record InflationForecastProperties(BigDecimal forecastPercent, String asOf, String source) {}
