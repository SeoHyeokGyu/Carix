package com.carix.scenario;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.scenario.dto.LatestScenarioResponse;
import com.carix.scenario.dto.SimulationRequest;
import com.carix.scenario.dto.SimulationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/scenarios")
@Tag(name = "Scenario Simulator", description = "잔류 vs 이직 보상 및 기회비용 모의계산 API")
public class ScenarioController {

  @Operation(
      summary = "잔류 vs 이직 보상 시뮬레이션 계산",
      description = "입력한 인상률 및 이직 목표 연봉, 공백 기간 등의 변수를 대입해 시나리오 시뮬레이션을 수행하고 결과를 저장합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "계산 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 범위값 입력",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/simulate")
  public ResponseEntity<ApiResult<SimulationResponse>> simulateScenarios(
      @Valid @RequestBody SimulationRequest request) {
    long currentSalary =
        request.currentGrossSalary() != null ? request.currentGrossSalary() : 60000000L;
    long stay12m = (long) (currentSalary * (1 + (request.stayExpectedRaiseRate() / 100)));
    long move12m =
        (long)
                (request.moveTargetGrossSalary()
                    * (12 - request.expectedUnemploymentMonths())
                    / 12.0)
            + (request.isEligibleForUnemploymentBenefit() ? 3960000L : 0L)
            - request.moveOneTimeCost();
    long diff = move12m - stay12m;

    String type = diff >= 0 ? "ESTIMATED_GAIN" : "ESTIMATED_OPPORTUNITY_COST";
    String typeLabel = diff >= 0 ? "추정 추가보상" : "추정 기회비용";
    String name = request.scenarioName() != null ? request.scenarioName() : "기본 시나리오";
    UUID id = UUID.randomUUID();

    // 실현 가능성 분석 로직 적용
    SimulationResponse.FeasibilityDetail feasibility =
        calculateFeasibility(request.moveTargetGrossSalary());

    return ResponseEntity.ok(
        ApiResult.success(
            new SimulationResponse(
                id,
                name,
                stay12m,
                move12m,
                request.isEligibleForUnemploymentBenefit() ? 3960000L : 0L,
                diff,
                type,
                typeLabel,
                "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
                feasibility),
            "시뮬레이션 모의계산이 완료되었습니다."));
  }

  @Operation(
      summary = "최근 시뮬레이션 시나리오 조회",
      description = "가장 마지막으로 시뮬레이션하여 저장한 시나리오의 입력 파라미터 및 계산 결과를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "204", description = "저장된 시나리오가 없는 경우 (No Content)")
  })
  @GetMapping("/latest")
  public ResponseEntity<ApiResult<LatestScenarioResponse>> getLatestScenario() {
    UUID id = UUID.fromString("e812d8a4-0cfb-4a5f-9dfb-12d88c91350c");
    long targetSalary = 72000000L;
    return ResponseEntity.ok(
        ApiResult.success(
            new LatestScenarioResponse(
                id,
                "A사 오퍼 제안",
                new SimulationRequest("A사 오퍼 제안", 60000000L, 3.5, targetSalary, 3000000L, 2, true),
                new SimulationResponse(
                    id,
                    "A사 오퍼 제안",
                    62100000L,
                    60960000L,
                    3960000L,
                    -1140000L,
                    "ESTIMATED_OPPORTUNITY_COST",
                    "추정 기회비용",
                    "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
                    calculateFeasibility(targetSalary)),
                Instant.now())));
  }

  @Operation(
      summary = "저장된 시뮬레이션 시나리오 전체 목록 조회",
      description = "사용자가 여태까지 계산하고 저장한 모든 시나리오 목록을 조회하여 서로 비교할 수 있도록 제공합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content =
          @Content(
              array =
                  @ArraySchema(schema = @Schema(implementation = LatestScenarioResponse.class))))
  @GetMapping
  public ResponseEntity<ApiResult<List<LatestScenarioResponse>>> getScenarios() {
    UUID id = UUID.fromString("e812d8a4-0cfb-4a5f-9dfb-12d88c91350c");
    long targetSalary = 72000000L;
    return ResponseEntity.ok(
        ApiResult.success(
            List.of(
                new LatestScenarioResponse(
                    id,
                    "A사 오퍼 제안",
                    new SimulationRequest(
                        "A사 오퍼 제안", 60000000L, 3.5, targetSalary, 3000000L, 2, true),
                    new SimulationResponse(
                        id,
                        "A사 오퍼 제안",
                        62100000L,
                        60960000L,
                        3960000L,
                        -1140000L,
                        "ESTIMATED_OPPORTUNITY_COST",
                        "추정 기회비용",
                        "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
                        calculateFeasibility(targetSalary)),
                    Instant.now()))));
  }

  @Operation(summary = "저장된 시뮬레이션 시나리오 삭제", description = "특정 고유 ID를 기반으로 저장된 가상 시나리오 이력을 삭제합니다.")
  @ApiResponse(responseCode = "200", description = "삭제 완료")
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResult<Void>> deleteScenario(@PathVariable UUID id) {
    return ResponseEntity.ok(ApiResult.success(null, "시나리오가 성공적으로 삭제되었습니다."));
  }

  // --- 실현 가능성 계산 헬퍼 함수 ---
  private SimulationResponse.FeasibilityDetail calculateFeasibility(long targetSalary) {
    // 기준 분포: min = 45M, p25 = 58M, median = 65M, p75 = 74M, max = 95M
    int percentile;
    if (targetSalary <= 45000000) {
      percentile = 5;
    } else if (targetSalary <= 58000000) {
      percentile = 5 + (int) ((targetSalary - 45000000.0) / (58000000.0 - 45000000.0) * 20);
    } else if (targetSalary <= 65000000) {
      percentile = 25 + (int) ((targetSalary - 58000000.0) / (65000000.0 - 58000000.0) * 25);
    } else if (targetSalary <= 74000000) {
      percentile = 50 + (int) ((targetSalary - 65000000.0) / (74000000.0 - 65000000.0) * 25);
    } else if (targetSalary <= 95000000) {
      percentile = 75 + (int) ((targetSalary - 74000000.0) / (95000000.0 - 74000000.0) * 20);
    } else {
      percentile = 95 + (int) Math.min(4, (targetSalary - 95000000.0) / 10000000.0);
    }

    int score = 100 - percentile;
    String grade;
    String gradeLabel;
    if (percentile < 30) {
      grade = "EASY";
      gradeLabel = "무난하게 달성 가능한";
    } else if (percentile < 60) {
      grade = "REALISTIC";
      gradeLabel = "실현 가능한 적정선 수준의";
    } else if (percentile < 85) {
      grade = "CHALLENGING";
      gradeLabel = "도전적인";
    } else {
      grade = "VERY_HIGH_CHALLENGE";
      gradeLabel = "매우 공격적인 최상위권";
    }

    String feedback =
        String.format(
            "목표 연봉은 동료집단 내 상위 %d%% 수준의 %s 목표입니다. %s",
            100 - percentile,
            gradeLabel,
            percentile >= 75
                ? "동료집단 내에서 상위권 연봉에 속하며 충분한 스킬 요건을 증명해야 할 수 있습니다."
                : "시장 평균 범위 내에 위치하여 원만히 조율 가능한 수준입니다.");

    return new SimulationResponse.FeasibilityDetail(score, percentile, grade, feedback);
  }
}
