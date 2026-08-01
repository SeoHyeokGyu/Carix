package com.carix.expense.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "지출 수정 요청")
public record ExpenseUpdateRequest(
    @NotNull @Schema(description = "수정할 지출 항목 리스트") List<ExpenseItemInput> items
) {

}
