package com.carix.reference.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "통계청 소비자물가지수(CPI) 기준 데이터 응답")
public record CpiReferenceResponse(
        @Schema(description = "지수 기준 년도", example = "2020") String baseYear,
        @Schema(description = "월별 지수 데이터") List<CpiMonthlyPoint> data
) {
    public record CpiMonthlyPoint(
            @Schema(description = "기준 년월 (YYYYMM)", example = "202606") String yearMonth,
            @Schema(description = "카테고리별 CPI 값 리스트") List<CpiCategoryPoint> categories
    ) {}

    public record CpiCategoryPoint(
            @Schema(description = "지출 카테고리 (HOUSING, FOOD, TRANSPORTATION, HEALTH, EDUCATION, OTHER)", example = "HOUSING") String category,
            @Schema(description = "소비자물가지수(CPI) 값", example = "108.5") double cpiValue
    ) {}
}
