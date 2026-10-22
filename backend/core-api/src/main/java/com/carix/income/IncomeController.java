package com.carix.income;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.income.dto.BonusTermsRequest;
import com.carix.income.dto.IncomeRequest;
import com.carix.income.dto.IncomeResponse;
import com.carix.income.service.IncomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/income")
@Tag(name = "Income Management", description = "소득 이력 등록, 조회, 수정 및 삭제 API")
public class IncomeController {

  private final IncomeService incomeService;

  public IncomeController(IncomeService incomeService) {
    this.incomeService = incomeService;
  }

  @Operation(summary = "소득 이력 전체 조회", description = "사용자가 등록한 연봉 및 실수령액의 모든 역사적 변경 이력을 반환합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content =
          @Content(array = @ArraySchema(schema = @Schema(implementation = IncomeResponse.class))))
  @GetMapping
  public ResponseEntity<ApiResult<List<IncomeResponse>>> getIncomeHistory(
      @RequestHeader("X-User-Id") UUID userId) {
    return ResponseEntity.ok(ApiResult.success(incomeService.getHistory(userId)));
  }

  @Operation(summary = "소득 정보 등록", description = "새로운 연봉 정보를 등록합니다. 동일한 유효 시작일에는 중복으로 등록할 수 없습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "등록 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 입력값",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "유효 시작일 중복 발생",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<ApiResult<IncomeResponse>> createIncome(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody IncomeRequest request) {
    ApiResult<IncomeResponse> response =
        ApiResult.success(incomeService.create(userId, request), "소득 정보가 정상적으로 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Operation(
      summary = "상여 조건 일괄 적용",
      description =
          "선택한 연봉 행과 그 이후 모든 행에 상여 포함 여부·따로 받는 고정 상여를 적용하고 전체 이력을 반환합니다. 들어 있음이면 고정 상여를 보낼 수 없고, 따로 받음이면 필수입니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "적용 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 입력값",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "기준 행이 없거나 본인 소유가 아님",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PutMapping("/bonus-terms")
  public ResponseEntity<?> updateBonusTerms(
      @RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody BonusTermsRequest request) {
    boolean included = Boolean.TRUE.equals(request.bonusIncluded());
    String conflict =
        included && request.fixedBonusAnnual() != null
            ? "상여가 들어 있으면 고정 상여를 보낼 수 없습니다."
            : !included && (request.fixedBonusAnnual() == null || request.fixedBonusAnnual() == 0)
                ? "상여를 따로 받으면 0원보다 큰 고정 상여 금액이 필요합니다."
                : null;
    if (conflict != null) {
      return ResponseEntity.badRequest()
          .body(
              ErrorResponse.of(
                  "INVALID_INPUT", "입력값 형식이 올바르지 않습니다.", Map.of("fixedBonusAnnual", conflict)));
    }
    return ResponseEntity.ok(
        ApiResult.success(incomeService.updateBonusTerms(userId, request), "상여 조건이 적용되었습니다."));
  }

  @Operation(
      summary = "소득 정보 수정",
      description =
          "특정 ID의 소득 정보를 수정합니다. 변경 결과 유효 시작일이 중복되면 충돌 오류를 반환하며, 성공 시 영향도 월에 대해 재계산이 트리거됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 입력값",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "수정하려는 시작일이 다른 이력과 중복됨",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PutMapping("/{id}")
  public ResponseEntity<ApiResult<IncomeResponse>> updateIncome(
      @RequestHeader("X-User-Id") UUID userId,
      @PathVariable UUID id,
      @Valid @RequestBody IncomeRequest request) {
    return ResponseEntity.ok(
        ApiResult.success(incomeService.update(userId, id, request), "소득 정보가 정상적으로 수정되었습니다."));
  }

  @Operation(
      summary = "소득 정보 삭제",
      description = "특정 ID의 소득 정보를 이력에서 제거합니다. 성공 시 관련된 구간의 데이터 재계산이 트리거됩니다.")
  @ApiResponse(responseCode = "200", description = "삭제 성공")
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResult<Void>> deleteIncome(
      @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
    incomeService.delete(userId, id);
    return ResponseEntity.ok(ApiResult.success(null, "소득 정보가 성공적으로 삭제되었습니다."));
  }
}
