package com.carix.expense;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.common.usercontext.CurrentUserId;
import com.carix.expense.dto.AppliedExpenseResponse;
import com.carix.expense.dto.ExpenseHistoryResponse;
import com.carix.expense.dto.ExpenseUpdateRequest;
import com.carix.expense.service.AppliedExpenseService;
import com.carix.expense.service.ExpenseService;
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
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/expenses")
@Tag(name = "Expense Management", description = "소비 카테고리 지출 금액 및 가중치 관리 API")
public class ExpenseController {

  private final ExpenseService expenseService;
  private final AppliedExpenseService appliedExpenseService;

  public ExpenseController(
      ExpenseService expenseService, AppliedExpenseService appliedExpenseService) {
    this.expenseService = expenseService;
    this.appliedExpenseService = appliedExpenseService;
  }

  @Operation(summary = "지출 이력 전체 조회", description = "사용자가 등록한 기준 월별 지출 기록 목록과 카테고리 가중치 상태를 반환합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content =
          @Content(
              array =
                  @ArraySchema(schema = @Schema(implementation = ExpenseHistoryResponse.class))))
  @GetMapping
  public ResponseEntity<ApiResult<List<ExpenseHistoryResponse>>> getExpenseHistory(
      @RequestHeader("X-User-Id") UUID userId) {
    return ResponseEntity.ok(ApiResult.success(expenseService.getHistory(userId)));
  }

  // =========================================================================
  // [Member C 소유 API / 화면은 S-09 검토] 그 달에 실제로 적용된 소비 금액·가중치
  // =========================================================================
  @Operation(
      summary = "적용된 소비 금액·가중치 조회",
      description =
          "그 달 지수 계산에 실제로 적용된 카테고리별 금액과 가중치를 반환합니다 (US-O6, S-09 검토 화면)."
              + " 통계청 평균을 그대로 내려주지 않습니다 — 사용자 입력·과거 월 전파·미입력분 보간 중"
              + " 무엇이 쓰였는지에 따라 답이 달라지고 amountBasis 가 그것을 말합니다."
              + " 절대 금액 기준이 없으면 amount 는 null 이고 가중치만 적용됩니다."
              + " 입력 화면(S-07)에서는 쓰지 않습니다 — 입력 전 평균 노출은 앵커링을 만듭니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content = @Content(schema = @Schema(implementation = AppliedExpenseResponse.class)))
  @GetMapping("/applied")
  public ResponseEntity<ApiResult<AppliedExpenseResponse>> getAppliedExpenses(
      @CurrentUserId UUID userId,
      @RequestParam(required = false)
          @Pattern(regexp = "^\\d{6}$")
          @Schema(description = "기준 년월 (YYYYMM). 미지정 시 최신 소비 이력 월", example = "202609")
          String baseYearMonth) {
    return ResponseEntity.ok(
        ApiResult.success(appliedExpenseService.resolve(userId, baseYearMonth)));
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
      @RequestHeader("X-User-Id") UUID userId,
      @PathVariable
          @Pattern(regexp = "^\\d{6}$")
          @Schema(description = "기준 년월 (YYYYMM)", example = "202607")
          String baseYearMonth,
      @Valid @RequestBody ExpenseUpdateRequest request) {
    ExpenseHistoryResponse saved;
    try {
      saved = expenseService.upsert(userId, baseYearMonth, request);
    } catch (DataIntegrityViolationException e) {
      // 그 달 첫 저장이 동시에 두 번 온 경우. 실패한 트랜잭션은 복구할 수 없어 밖에서 한 번만 다시 한다 —
      // 새 트랜잭션은 먼저 들어간 행을 찾아 고친다(ScenarioController 초안 저장과 같은 방식).
      saved = expenseService.upsert(userId, baseYearMonth, request);
    }
    return ResponseEntity.ok(ApiResult.success(saved, "지출 정보가 업데이트되었습니다."));
  }
}
