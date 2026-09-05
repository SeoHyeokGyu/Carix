package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "지수 트렌드 차트 데이터 응답")
public record TrendListResponse(
    List<TrendPoint> trends,
    @Schema(description = "구매력 지수 계열 산출 상태", example = "OK") String purchasingPowerStatus,
    @Schema(description = "시장가치 지수 계열 산출 상태", example = "OK") String marketValueStatus) {

  /**
   * 한 달의 지수 점. 그 달을 계산하지 못했으면 값이 {@code null} 이고 차트는 선을 잇지 않고 갭으로 그린다 (기능 명세 §5 — {@code 일부 월 누락 시
   * 갭 표시, 선 연결 금지}).
   */
  public record TrendPoint(
      String yearMonth,
      @Schema(nullable = true) Double purchasingPowerIndex,
      @Schema(nullable = true) Double marketValueIndex,
      @Schema(nullable = true) Double incomeIndex,
      @Schema(nullable = true) Double personalPriceIndex,
      @Schema(nullable = true) Long nominalIncome,
      @Schema(nullable = true) Long realIncome) {}
}
