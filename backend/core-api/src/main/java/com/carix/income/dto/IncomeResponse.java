package com.carix.income.dto;

import com.carix.income.domain.IncomeChangeReasonEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "소득 정보 응답")
public record IncomeResponse(
    @Schema(description = "소득 이력 고유 ID") UUID id,
    @Schema(description = "세전 연봉 (원)", example = "60000000") long grossSalary,
    @Schema(description = "월 실수령액 (원)", example = "4200000") long netSalary,
    @Schema(description = "유효 시작일 (소득 적용일)", example = "2026-01-01") LocalDate effectiveDate,
    @Schema(description = "표시용 변경 사유. 가장 이른 유효일 행이면 INITIAL 이다.", example = "RAISE")
        IncomeChangeReasonEnum changeReason,
    @Schema(
            description = "사용자가 고른 값 그대로. 편집 폼이 이것으로 컨트롤을 채운다. INITIAL 이 담기지 않는다.",
            example = "RAISE",
            allowableValues = {"RAISE", "JOB_CHANGE"})
        IncomeChangeReasonEnum selectedChangeReason) {}
