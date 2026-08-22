package com.carix.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세부 카테고리 지출 항목")
public record ExpenseItem(
    @Schema(
            description = "지출 분류 (HOUSING, FOOD, TRANSPORTATION, HEALTH, EDUCATION, OTHER)",
            example = "HOUSING")
        String category,
    @Schema(description = "지출 금액 (원)", example = "600000") long amount,
    @Schema(description = "수치 출처 상태 (USER_DEFINED, DEFAULT_CPI)", example = "USER_DEFINED")
        String status) {}
