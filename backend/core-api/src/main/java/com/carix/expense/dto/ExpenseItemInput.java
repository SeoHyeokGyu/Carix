package com.carix.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "지출 항목 입력 스키마")
public record ExpenseItemInput(
    @NotBlank @Schema(description = "카테고리명", example = "HOUSING") String category,
    @NotNull @PositiveOrZero @Schema(description = "지출 금액 (원)", example = "600000") Long amount) {}
