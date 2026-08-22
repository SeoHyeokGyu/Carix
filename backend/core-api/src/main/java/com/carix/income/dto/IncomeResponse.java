package com.carix.income.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "소득 정보 응답")
public record IncomeResponse(
    @Schema(description = "소득 이력 고유 ID") UUID id,
    @Schema(description = "세전 연봉 (원)", example = "60000000") long grossSalary,
    @Schema(description = "월 실수령액 (원)", example = "4200000") long netSalary,
    @Schema(description = "유효 시작일 (소득 적용일)", example = "2026-01-01") LocalDate effectiveDate) {}
