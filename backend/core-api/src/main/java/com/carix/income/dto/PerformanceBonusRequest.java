package com.carix.income.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Schema(description = "성과급 수령 기록 요청")
public record PerformanceBonusRequest(
    @NotNull
        @Pattern(regexp = "\\d{6}", message = "지급월은 yyyyMM 형식이어야 합니다.")
        @Schema(description = "지급월 (yyyyMM)", example = "202607")
        String paidMonth,
    @NotNull @Positive @Schema(description = "지급 금액 (원)", example = "3000000") Long amount) {}
