package com.carix.expense;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.expense.dto.ExpenseHistoryResponse;
import com.carix.expense.dto.ExpenseItem;
import com.carix.expense.dto.ExpenseUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/expenses")
@Tag(name = "Expense Management", description = "소비 카테고리 지출 금액 및 가중치 관리 API")
public class ExpenseController {

  @Operation(summary = "지출 이력 전체 조회", description = "사용자가 등록한 기준 월별 지출 기록 목록과 카테고리 가중치 상태를 반환합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content =
          @Content(
              array =
                  @ArraySchema(schema = @Schema(implementation = ExpenseHistoryResponse.class))))
  @GetMapping
  public ResponseEntity<ApiResult<List<ExpenseHistoryResponse>>> getExpenseHistory() {
    return ResponseEntity.ok(
        ApiResult.success(
            List.of(
                new ExpenseHistoryResponse(
                    "202607",
                    List.of(
                        new ExpenseItem("HOUSING", 600000L, "USER_DEFINED"),
                        new ExpenseItem("FOOD", 400000L, "USER_DEFINED"),
                        new ExpenseItem("TRANSPORTATION", 300000L, "USER_DEFINED"),
                        new ExpenseItem("HEALTH", 100000L, "USER_DEFINED"),
                        new ExpenseItem("EDUCATION", 200000L, "USER_DEFINED"),
                        new ExpenseItem("OTHER", 1192000L, "DEFAULT_CPI")),
                    "PARTIALLY_ESTIMATED"))));
  }

  @Operation(
      summary = "특정월 지출 등록 및 수정",
      description = "지정된 기준 월에 소비 카테고리별 지출 금액을 등록하거나 수정합니다. 미입력 카테고리는 통계청 가계동향조사 기준값으로 자동 보간됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "등록/수정 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 입력 양식",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PutMapping("/{baseYearMonth}")
  public ResponseEntity<ApiResult<ExpenseHistoryResponse>> updateExpense(
      @PathVariable
          @Pattern(regexp = "^\\d{6}$")
          @Schema(description = "기준 년월 (YYYYMM)", example = "202607")
          String baseYearMonth,
      @Valid @RequestBody ExpenseUpdateRequest request) {
    return ResponseEntity.ok(
        ApiResult.success(
            new ExpenseHistoryResponse(
                baseYearMonth,
                request.items().stream()
                    .map(item -> new ExpenseItem(item.category(), item.amount(), "USER_DEFINED"))
                    .toList(),
                "USER_DEFINED"),
            "지출 정보가 업데이트되었습니다."));
  }
}
