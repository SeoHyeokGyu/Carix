package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "지수 트렌드 차트 데이터 응답")
public record TrendListResponse(List<TrendPoint> trends) {

  public record TrendPoint(
      String yearMonth,
      double purchasingPowerIndex,
      double marketValueIndex,
      double incomeIndex,
      double personalPriceIndex,
      long nominalIncome,
      long realIncome
  ) {

  }
}
