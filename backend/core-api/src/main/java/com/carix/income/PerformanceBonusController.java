package com.carix.income;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.income.dto.PerformanceBonusRequest;
import com.carix.income.dto.PerformanceBonusResponse;
import com.carix.income.service.PerformanceBonusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/income/bonuses")
@Tag(name = "Income Management", description = "소득 이력 등록, 조회, 수정 및 삭제 API")
public class PerformanceBonusController {

  private final PerformanceBonusService service;

  public PerformanceBonusController(PerformanceBonusService service) {
    this.service = service;
  }

  @Operation(summary = "성과급 수령 기록 조회", description = "기록한 성과급을 지급월 내림차순으로 반환합니다.")
  @GetMapping
  public ResponseEntity<ApiResult<List<PerformanceBonusResponse>>> list(
      @RequestHeader("X-User-Id") UUID userId) {
    return ResponseEntity.ok(ApiResult.success(service.list(userId)));
  }

  @Operation(
      summary = "성과급 수령 기록",
      description = "받은 성과급을 지급월·금액으로 한 건 기록합니다. 같은 달에 여러 건을 기록할 수 있습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "기록 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 입력값 (형식 오류, 존재하지 않는 월, 미래 지급월)",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<ApiResult<PerformanceBonusResponse>> create(
      @RequestHeader("X-User-Id") UUID userId,
      @Valid @RequestBody PerformanceBonusRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.success(service.create(userId, request), "성과급이 기록되었습니다."));
  }

  @Operation(summary = "성과급 수령 기록 삭제")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "404",
        description = "기록이 없거나 본인 소유가 아님",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @RequestHeader("X-User-Id") UUID userId, @PathVariable UUID id) {
    service.delete(userId, id);
    return ResponseEntity.noContent().build();
  }
}
