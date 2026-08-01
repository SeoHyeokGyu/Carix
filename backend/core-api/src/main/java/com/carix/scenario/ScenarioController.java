package com.carix.scenario;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.scenario.dto.LatestScenarioResponse;
import com.carix.scenario.dto.SimulationRequest;
import com.carix.scenario.dto.SimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/scenarios")
@Tag(name = "Scenario Simulator", description = "잔류 vs 이직 보상 및 기회비용 모의계산 API")
public class ScenarioController {

  @Operation(summary = "잔류 vs 이직 보상 시뮬레이션 계산", description = "입력한 인상률 및 이직 목표 연봉, 공백 기간 등의 변수를 대입해 시나리오 시뮬레이션을 수행하고 결과를 저장합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "계산 성공"),
      @ApiResponse(responseCode = "400", description = "잘못된 범위값 입력", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/simulate")
  public ResponseEntity<ApiResult<SimulationResponse>> simulateScenarios(
      @Valid @RequestBody SimulationRequest request) {
    long currentSalary =
        request.currentGrossSalary() != null ? request.currentGrossSalary() : 60000000L;
    long stay12m = (long) (currentSalary * (1 + (request.stayExpectedRaiseRate() / 100)));
    long move12m =
        (long) (request.moveTargetGrossSalary() * (12 - request.expectedUnemploymentMonths())
            / 12.0)
            + (request.isEligibleForUnemploymentBenefit() ? 3960000L : 0L)
            - request.moveOneTimeCost();
    long diff = move12m - stay12m;

    String type = diff >= 0 ? "ESTIMATED_GAIN" : "ESTIMATED_OPPORTUNITY_COST";
    String typeLabel = diff >= 0 ? "추정 추가보상" : "추정 기회비용";

    return ResponseEntity.ok(ApiResult.success(
        new SimulationResponse(
            stay12m,
            move12m,
            request.isEligibleForUnemploymentBenefit() ? 3960000L : 0L,
            diff,
            type,
            typeLabel,
            "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다."
        ),
        "시뮬레이션 모의계산이 완료되었습니다."
    ));
  }

  @Operation(summary = "최근 시뮬레이션 시나리오 조회", description = "가장 마지막으로 시뮬레이션하여 저장한 시나리오의 입력 파라미터 및 계산 결과를 반환합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "성공"),
      @ApiResponse(responseCode = "240", description = "저장된 시나리오가 없는 경우 (No Content)")
      // 204 No Content
  })
  @GetMapping("/latest")
  public ResponseEntity<ApiResult<LatestScenarioResponse>> getLatestScenario() {
    return ResponseEntity.ok(ApiResult.success(
        new LatestScenarioResponse(
            new SimulationRequest(60000000L, 3.5, 72000000L, 3000000L, 2, true),
            new SimulationResponse(
                62100000L,
                60960000L,
                3960000L,
                -1140000L,
                "ESTIMATED_OPPORTUNITY_COST",
                "추정 기회비용",
                "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다."
            ),
            Instant.now()
        )
    ));
  }
}
