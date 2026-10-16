package com.carix.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.common.usercontext.UserContextArgumentResolver;
import com.carix.scenario.dto.LatestScenarioResponse;
import com.carix.scenario.dto.SimulationRequest;
import com.carix.scenario.dto.SimulationResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
        MockMvcBuilders.standaloneSetup(new ScenarioController(scenarioService))
            .setCustomArgumentResolvers(new UserContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  @DisplayName("POST /v1/scenarios/simulate - 계산 결과를 그대로 싣는다")
  void simulate() throws Exception {
    given(scenarioService.simulate(eq(USER_ID), any(SimulationRequest.class)))
        .willReturn(response());

    mockMvc
        .perform(
            post("/v1/scenarios/simulate")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.stayCompensation12m").value(62100000))
        .andExpect(jsonPath("$.data.difference").value(-1140000))
        .andExpect(jsonPath("$.data.type").value("ESTIMATED_OPPORTUNITY_COST"))
        .andExpect(jsonPath("$.data.unemploymentBenefitNote").value("가정 기반 추정 (기준 시점: 2026-01)"))
        .andExpect(jsonPath("$.data.feasibility.status").value("OK"))
        .andExpect(jsonPath("$.data.feasibility.targetPercentile").value(69));
  }

  @Test
  @DisplayName("POST /v1/scenarios/simulate - X-User-Id 가 없으면 401 이다")
  void simulate_인증없음() throws Exception {
    mockMvc
        .perform(
            post("/v1/scenarios/simulate").contentType(MediaType.APPLICATION_JSON).content(body()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /v1/scenarios/simulate - 공백 개월수가 범위를 벗어나면 400 이다")
  void simulate_범위초과() throws Exception {
    mockMvc
        .perform(
            post("/v1/scenarios/simulate")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body()
                        .replace(
                            "\"expectedUnemploymentMonths\": 2",
                            "\"expectedUnemploymentMonths\": 7")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /v1/scenarios/simulate - 인상률이 범위를 벗어나면 400 이다 — DB 오버플로로 넘기지 않는다")
  void simulate_인상률_범위초과() throws Exception {
    mockMvc
        .perform(
            post("/v1/scenarios/simulate")
                .header("X-User-Id", USER_ID.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body()
                        .replace(
                            "\"stayExpectedRaiseRate\": 3.5", "\"stayExpectedRaiseRate\": 1000.0")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("GET /v1/scenarios/latest - 저장분이 있으면 입력과 결과를 함께 돌려준다")
  void latest() throws Exception {
    given(scenarioService.latest(USER_ID))
        .willReturn(
            Optional.of(
                new LatestScenarioResponse(
                    response().id(),
                    "A사 오퍼 제안",
                    new SimulationRequest(
                        "A사 오퍼 제안", 60_000_000L, 3.5, 72_000_000L, 3_000_000L, 2, true),
                    response(),
                    Instant.parse("2026-07-18T17:00:00Z"))));

    mockMvc
        .perform(get("/v1/scenarios/latest").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.scenarioName").value("A사 오퍼 제안"))
        .andExpect(jsonPath("$.data.inputs.currentGrossSalary").value(60000000))
        .andExpect(jsonPath("$.data.result.difference").value(-1140000))
        .andExpect(jsonPath("$.data.calculatedAt").exists());
  }

  @Test
  @DisplayName("GET /v1/scenarios/latest - 저장분이 없으면 204 다")
  void latest_없음() throws Exception {
    given(scenarioService.latest(USER_ID)).willReturn(Optional.empty());

    mockMvc
        .perform(get("/v1/scenarios/latest").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("GET /v1/scenarios - 저장분을 목록으로 돌려준다")
  void list() throws Exception {
    given(scenarioService.list(USER_ID))
        .willReturn(
            List.of(
                new LatestScenarioResponse(
                    response().id(),
                    null,
                    new SimulationRequest(null, 60_000_000L, 3.5, 72_000_000L, 3_000_000L, 2, true),
                    response(),
                    Instant.parse("2026-07-18T17:00:00Z"))));

    mockMvc
        .perform(get("/v1/scenarios").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value("e812d8a4-0cfb-4a5f-9dfb-12d88c91350c"))
        .andExpect(jsonPath("$.data[0].inputs.moveTargetGrossSalary").value(72000000))
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

  private String body() {
    return """
        {
          "scenarioName": "A사 오퍼 제안",
          "currentGrossSalary": 60000000,
          "stayExpectedRaiseRate": 3.5,
          "moveTargetGrossSalary": 72000000,
          "moveOneTimeCost": 3000000,
          "expectedUnemploymentMonths": 2,
          "isEligibleForUnemploymentBenefit": true
        }
        """;
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
        "추정 기회비용",
        "이 계산에는 세금, 성과급, 퇴직금 등이 포함되지 않은 단순 세전 기준 시뮬레이션입니다.",
        new SimulationResponse.FeasibilityDetail(
            "OK", 42, null, 31, 69, "CHALLENGING", "목표 연봉은 동료집단 내 상위 31% 수준의 도전적인 목표입니다."));
  }
}
