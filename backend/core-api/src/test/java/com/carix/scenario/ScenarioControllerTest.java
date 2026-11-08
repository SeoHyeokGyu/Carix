package com.carix.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.common.usercontext.UserContextArgumentResolver;
import com.carix.scenario.dto.EvaluateRequest;
import com.carix.scenario.dto.ScenarioListItem;
import com.carix.scenario.dto.ScenarioListItem.LegacyScenario;
import com.carix.scenario.dto.SimulationRequest;
import com.carix.scenario.dto.SimulationResponse;
import com.carix.scenario.evaluation.ScenarioContextService;
import com.carix.scenario.evaluation.ScenarioEvaluationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScenarioControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private MockMvc mockMvc;
  private ScenarioService scenarioService;

  @BeforeEach
  void setUp() {
    scenarioService = mock(ScenarioService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ScenarioController(
                    scenarioService,
                    mock(ScenarioContextService.class),
                    mock(ScenarioEvaluationService.class)))
            .setCustomArgumentResolvers(new UserContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("GET /v1/scenarios - 저장분을 목록으로 돌려준다")
  void list() throws Exception {
    given(scenarioService.list(USER_ID))
        .willReturn(
            List.of(
                new ScenarioListItem(
                    response().id(),
                    null,
                    1,
                    OffsetDateTime.parse("2026-07-18T17:00:00Z"),
                    null,
                    null,
                    new LegacyScenario(
                        new SimulationRequest(
                            null, 60_000_000L, 3.5, 72_000_000L, 3_000_000L, 2, true),
                        response()),
                    null)));

    mockMvc
        .perform(get("/v1/scenarios").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value("e812d8a4-0cfb-4a5f-9dfb-12d88c91350c"))
        .andExpect(jsonPath("$.data[0].legacy.inputs.moveTargetGrossSalary").value(72000000))
        .andExpect(jsonPath("$.data[0].calculatedAt").exists());
  }

  @Test
  @DisplayName("GET /v1/scenarios - 저장분이 없으면 200 과 빈 배열이다")
  void list_없음() throws Exception {
    given(scenarioService.list(USER_ID)).willReturn(List.of());

    mockMvc
        .perform(get("/v1/scenarios").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  @DisplayName("GET /v1/scenarios - X-User-Id 가 없으면 401 이다")
  void list_인증없음() throws Exception {
    mockMvc.perform(get("/v1/scenarios")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PUT /v1/scenarios/draft - 동시 첫 저장 충돌은 한 번 다시 해 204, 다시 충돌하면 409")
  void saveDraft_동시저장_재시도() throws Exception {
    String body = "{\"stay\":{\"raisePercent\":3}}";
    doThrow(new DataIntegrityViolationException("uk_draft"))
        .doNothing()
        .when(scenarioService)
        .saveDraft(eq(USER_ID), any(EvaluateRequest.class));

    mockMvc
        .perform(
            put("/v1/scenarios/draft")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());
    verify(scenarioService, times(2)).saveDraft(eq(USER_ID), any(EvaluateRequest.class));

    doThrow(new DataIntegrityViolationException("uk_draft"))
        .when(scenarioService)
        .saveDraft(eq(USER_ID), any(EvaluateRequest.class));

    // 다시 충돌해도 500 이 아니라 409 다 — 공통 처리기가 DB 메시지(사용자 ID 포함)를 싣지 않고 답한다.
    mockMvc
        .perform(
            put("/v1/scenarios/draft")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict());
  }

  private SimulationResponse response() {
    return new SimulationResponse(
        UUID.fromString("e812d8a4-0cfb-4a5f-9dfb-12d88c91350c"),
        "A사 오퍼 제안",
        62_100_000L,
        60_960_000L,
        3_960_000L,
        "가정 기반 추정 (기준 시점: 2026-01)",
        -1_140_000L,
        "ESTIMATED_OPPORTUNITY_COST",
        "남으면 추정 이득",
        "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
        new SimulationResponse.FeasibilityDetail(
            "OK", 42, null, 31, 69, "CHALLENGING", "새 회사 연봉은 동료집단 상위 31% 수준으로, 높은 편입니다."));
  }
}
