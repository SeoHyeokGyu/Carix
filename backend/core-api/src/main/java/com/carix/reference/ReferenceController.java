package com.carix.reference;

import com.carix.common.ApiResult;
import com.carix.reference.dto.DefaultExpensesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/reference")
@Tag(name = "Reference Data", description = "시스템 공통 통계 기준 및 기본값 정보 제공 API")
public class ReferenceController {

  @Operation(summary = "통계청 기준 카테고리별 기본 지출 가중치 조회", description = "사용자가 온보딩 단계 등에서 입력하지 않은 지출 분야가 있을 때 대체할 통계청 공식 평균 가중치 및 지출 금액의 기본 기준 데이터를 조회합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @GetMapping("/default-expenses")
  public ResponseEntity<ApiResult<DefaultExpensesResponse>> getDefaultExpenses() {
    return ResponseEntity.ok(ApiResult.success(
        new DefaultExpensesResponse(
            "2025-v1",
            List.of(
                new DefaultExpensesResponse.DefaultExpenseCategory("HOUSING", 0.174, 511000L),
                new DefaultExpensesResponse.DefaultExpenseCategory("FOOD", 0.142, 417000L),
                new DefaultExpensesResponse.DefaultExpenseCategory("TRANSPORTATION", 0.123,
                    361000L),
                new DefaultExpensesResponse.DefaultExpenseCategory("HEALTH", 0.081, 238000L),
                new DefaultExpensesResponse.DefaultExpenseCategory("EDUCATION", 0.075, 220000L),
                new DefaultExpensesResponse.DefaultExpenseCategory("OTHER", 0.405, 1192000L)
            )
        )
    ));
  }
}
