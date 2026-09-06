package com.carix.scenario;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.common.usercontext.CurrentUserId;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/scenarios")
@RequiredArgsConstructor
@Tag(name = "Scenario Simulator", description = "잔류 vs 이직 보상 및 기회비용 모의계산 API")
public class ScenarioController {

  private final ScenarioService scenarioService;

  @Operation(
      summary = "잔류 vs 이직 보상 시뮬레이션 계산",
      description = "입력한 인상률 및 이직 목표 연봉, 공백 기간 등의 변수를 대입해 시나리오 시뮬레이션을 수행하고 결과를 저장합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "계산 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 범위값 입력, 또는 currentGrossSalary 를 생략했는데 소득 이력도 없는 경우",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/simulate")
  public ResponseEntity<ApiResult<SimulationResponse>> simulateScenarios(
      @CurrentUserId UUID userId, @Valid @RequestBody SimulationRequest request) {

    return ResponseEntity.ok(
        ApiResult.success(scenarioService.simulate(userId, request), "시뮬레이션 모의계산이 완료되었습니다."));
  }

  @Operation(
      summary = "최근 시뮬레이션 시나리오 조회",
      description = "가장 마지막으로 시뮬레이션하여 저장한 시나리오의 입력 파라미터 및 계산 결과를 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "204", description = "저장된 시나리오가 없는 경우 (No Content)")
  })
  @GetMapping("/latest")
  public ResponseEntity<ApiResult<LatestScenarioResponse>> getLatestScenario(
      @CurrentUserId UUID userId) {

    return scenarioService
        .latest(userId)
        .map(latest -> ResponseEntity.ok(ApiResult.success(latest)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }
}
