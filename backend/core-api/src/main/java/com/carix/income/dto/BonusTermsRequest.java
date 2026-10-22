package com.carix.income.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

@Schema(description = "상여 조건 일괄 적용 요청. 선택한 연봉 행과 그 이후 모든 행에 같은 조건을 쓴다.")
public record BonusTermsRequest(
    @NotNull @Schema(description = "이 행부터 이후 모든 행에 적용한다") UUID applyFromIncomeId,
    @NotNull @Schema(description = "연봉 계약 금액에 상여가 들어 있는가", example = "false") Boolean bonusIncluded,
    @PositiveOrZero
        @Schema(
            description = "계약 금액 밖에서 따로 받는 고정 상여의 연 금액 (원). 따로 받음이면 필수, 들어 있음이면 보낼 수 없다.",
            example = "6000000")
        Long fixedBonusAnnual) {}
