package com.carix.scenario;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.common.usercontext.CurrentUserId;
import com.carix.scenario.dto.EvaluateRequest;
import com.carix.scenario.dto.EvaluationResponse;
import com.carix.scenario.dto.SaveScenarioRequest;
import com.carix.scenario.dto.ScenarioContextResponse;
import com.carix.scenario.dto.ScenarioListItem;
import com.carix.scenario.dto.SimulationResponse;
import com.carix.scenario.evaluation.ScenarioContextService;
import com.carix.scenario.evaluation.ScenarioEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/scenarios")
@RequiredArgsConstructor
@Tag(name = "Scenario Simulator", description = "잔류 vs 이직 보상 및 기회비용 모의계산 API")
public class ScenarioController {

  private final ScenarioService scenarioService;
  private final ScenarioContextService contextService;
  private final ScenarioEvaluationService evaluationService;

  @Operation(
      summary = "시나리오 저장",
      description = "입력을 서버에서 다시 평가해 결과와 함께 명시적으로 저장합니다. 요청·결과 JSON 은 암호화돼 저장됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "저장 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "범위 밖 입력, 또는 소득 이력도 currentGrossOverride 도 없는 경우",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<ApiResult<ScenarioListItem>> saveScenario(
      @CurrentUserId UUID userId, @Valid @RequestBody SaveScenarioRequest request) {

    EvaluationResponse result = evaluationService.evaluate(userId, request.request());
    ScenarioListItem saved =
        scenarioService.save(userId, request.name(), request.request(), result);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResult.success(saved, "시나리오가 저장되었습니다."));
  }

  @Operation(
      summary = "저장된 시나리오 목록 조회",
      description =
          "저장한 시나리오 전부(초안 제외)를 최근 순으로 반환합니다. 엔진 2 는 request·result, 이전 방식(엔진 1)은 legacy 에 저장값 그대로입니다. 없으면 빈 배열입니다.")
  @ApiResponse(responseCode = "200", description = "성공")
  @GetMapping
  public ResponseEntity<ApiResult<List<ScenarioListItem>>> listScenarios(
      @CurrentUserId UUID userId) {

    return ResponseEntity.ok(ApiResult.success(scenarioService.list(userId)));
  }

  @Operation(summary = "초안 저장", description = "편집 중인 입력을 사용자당 1건으로 덮어씁니다. 결과는 저장하지 않습니다.")
  @ApiResponse(responseCode = "204", description = "저장 성공 (No Content)")
  @PutMapping("/draft")
  public ResponseEntity<Void> saveDraft(
      @CurrentUserId UUID userId, @Valid @RequestBody EvaluateRequest request) {

    scenarioService.saveDraft(userId, request);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "초안 조회", description = "저장해 둔 편집 중 입력을 반환합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(responseCode = "204", description = "초안이 없는 경우 (No Content)")
  })
  @GetMapping("/draft")
  public ResponseEntity<ApiResult<EvaluateRequest>> getDraft(@CurrentUserId UUID userId) {

    return scenarioService
        .draft(userId)
        .map(draft -> ResponseEntity.ok(ApiResult.success(draft)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @Operation(
      summary = "시뮬레이터 입력 기본값",
      description = "현재 소득·기본 인상률·성과급 범위·물가 두 기준·시장 격차를 한 번에 돌려줍니다. 소득 이력이 없으면 해당 값은 null 입니다.")
  @ApiResponse(responseCode = "200", description = "성공")
  @GetMapping("/context")
  public ResponseEntity<ApiResult<ScenarioContextResponse>> getContext(@CurrentUserId UUID userId) {

    return ResponseEntity.ok(ApiResult.success(contextService.context(userId)));
  }

  @Operation(summary = "목표 연봉의 실현 가능성", description = "저장하지 않고 동료집단 분포 내 백분위만 계산합니다.")
  @ApiResponse(responseCode = "200", description = "성공")
  @GetMapping("/feasibility")
  public ResponseEntity<ApiResult<SimulationResponse.FeasibilityDetail>> getFeasibility(
      @CurrentUserId UUID userId, @RequestParam long targetGross) {

    if (targetGross < 0) {
      throw new IllegalArgumentException("targetGross 는 0 이상이어야 합니다.");
    }
    return ResponseEntity.ok(
        ApiResult.success(scenarioService.feasibilityDetail(userId, targetGross)));
  }

  @Operation(
      summary = "잔류 vs 이직 세후·실질 평가",
      description = "48개월 세후·실질 투영, 기대층 범위, 불확실층 주식 줄, 퇴직금 경고, 가정·미반영 목록을 돌려줍니다. 저장하지 않습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "계산 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "범위 밖 입력, 또는 소득 이력도 currentGrossOverride 도 없는 경우",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/evaluate")
  public ResponseEntity<ApiResult<EvaluationResponse>> evaluate(
      @CurrentUserId UUID userId, @Valid @RequestBody EvaluateRequest request) {

    return ResponseEntity.ok(ApiResult.success(evaluationService.evaluate(userId, request)));
  }
}
