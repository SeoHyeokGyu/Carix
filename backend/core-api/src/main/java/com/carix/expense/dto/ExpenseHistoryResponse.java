package com.carix.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "월별 지출 상태 및 아이템 응답")
public record ExpenseHistoryResponse(
    @Schema(description = "기준 년월", example = "202607") String baseYearMonth,
    @Schema(description = "카테고리별 세부 지출 항목") List<ExpenseItem> items,
    @Schema(
            description =
                "월 종합 지출 가중치 상태 (USER_DEFINED, PARTIALLY_ESTIMATED, PROPAGATED, DEFAULT_CPI)",
            example = "PARTIALLY_ESTIMATED")
        String weightStatus) {}
