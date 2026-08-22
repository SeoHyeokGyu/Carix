package com.carix.reference.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "기준 지출 가중치 종합 응답")
public record DefaultExpensesResponse(
    @Schema(description = "기준 버전 정보", example = "2025-v1") String version,
    @Schema(description = "카테고리별 통계 데이터 리스트") List<DefaultExpenseCategory> categories) {

  public record DefaultExpenseCategory(
      @Schema(
              description = "지출 분야명 (HOUSING, FOOD, TRANSPORTATION, HEALTH, EDUCATION, OTHER)",
              example = "HOUSING")
          String category,
      @Schema(description = "평균 소비 비중 가중치 (비율)", example = "0.174") double weight,
      @Schema(description = "월평균 지출 금액 (원)", example = "511000") long averageAmount) {}
}
