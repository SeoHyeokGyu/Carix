package com.carix.scenario.inflation;

import com.carix.index.inflation.InflationComparison;
import com.carix.index.inflation.PersonalInflationService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 앞으로 12개월 물가 가정을 한국은행 전망치와 개인물가 반영치 두 기준으로 만든다. */
@Component
public class InflationAssumptions {

  /** 개인물가를 따로 보여 줄 만큼 총지수와 다른지 가르는 격차 기준(%p). */
  public static final BigDecimal INFLATION_GAP_THRESHOLD_PP = new BigDecimal("0.3");

  /** 개인물가를 계산하지 못해 한국은행 전망치로 대신했음을 알리는 가정 코드. */
  public static final String PERSONAL_INFLATION_UNAVAILABLE = "PERSONAL_INFLATION_UNAVAILABLE";

  private final InflationForecastProperties forecast;
  private final PersonalInflationService personalInflationService;

  public InflationAssumptions(
      InflationForecastProperties forecast, PersonalInflationService personalInflationService) {
    this.forecast = forecast;
    this.personalInflationService = personalInflationService;
  }

  public InflationBasis resolve(UUID userId, String baseYearMonth) {
    BigDecimal government = forecast.forecastPercent();
    Optional<InflationComparison> comparison =
        personalInflationService.trailing12m(userId, baseYearMonth);
    if (comparison.isEmpty()) {
      return new InflationBasis(
          government, null, null, null, false, forecast.asOf(), forecast.source());
    }
    InflationComparison c = comparison.get();
    return new InflationBasis(
        government,
        government.add(c.gapPp()),
        c.gapPp(),
        c.topGapCategory(),
        c.gapPp().abs().compareTo(INFLATION_GAP_THRESHOLD_PP) < 0,
        forecast.asOf(),
        forecast.source());
  }

  /** 계산에 쓸 물가율. 개인물가가 없으면 한국은행 전망치. */
  public BigDecimal effective(InflationBasis basis) {
    return basis.personalPercent() != null ? basis.personalPercent() : basis.governmentPercent();
  }
}
