package com.carix.income.dto;

import com.carix.income.domain.IncomeChangeReasonEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

@Schema(description = "소득 정보 등록/수정 요청")
public record IncomeRequest(
    @NotNull @PositiveOrZero @Schema(description = "세전 연봉 (원)", example = "65000000")
        Long grossSalary,
    @NotNull @PositiveOrZero @Schema(description = "월 실수령액 (원)", example = "4500000")
        Long netSalary,
    @NotNull @Schema(description = "유효 시작일 (소득 적용일)", example = "2026-07-01")
        LocalDate effectiveDate,
    @Schema(
            description = "변경 사유. 선택이며 미지정은 null 로 저장한다. INITIAL 은 보낼 수 없다 — 최초 소득 행은 서버가 판정한다.",
            example = "RAISE",
            allowableValues = {"RAISE", "JOB_CHANGE"})
        IncomeChangeReasonEnum changeReason) {}
