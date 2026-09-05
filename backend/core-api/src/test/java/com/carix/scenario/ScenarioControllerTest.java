package com.carix.scenario;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScenarioControllerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new ScenarioController()).build();
  }

  @Test
  @DisplayName("POST /v1/scenarios/simulate - 추정 추가보상 (GAIN) 시나리오")
  void simulateScenarios_gain() throws Exception {
    String body =
        """
        {
          "scenarioName": "대기업 이직",
          "currentGrossSalary": 60000000,
          "stayExpectedRaiseRate": 5.0,
          "moveTargetGrossSalary": 90000000,
          "moveOneTimeCost": 1000000,
          "expectedUnemploymentMonths": 1,
          "isEligibleForUnemploymentBenefit": true
        }
        """;

    mockMvc
        .perform(
            post("/v1/scenarios/simulate").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.scenarioName").value("대기업 이직"))
        .andExpect(jsonPath("$.data.type").value("ESTIMATED_GAIN"))
        .andExpect(jsonPath("$.data.feasibility.difficultyGrade").value("VERY_HIGH_CHALLENGE"));
  }

  @Test
  @DisplayName("POST /v1/scenarios/simulate - 추정 기회비용 (OPPORTUNITY_COST) 및 기본값 시나리오")
  void simulateScenarios_opportunityCost_and_defaults() throws Exception {
    String body =
        """
        {
          "currentGrossSalary": null,
          "stayExpectedRaiseRate": 10.0,
          "moveTargetGrossSalary": 40000000,
          "moveOneTimeCost": 5000000,
          "expectedUnemploymentMonths": 3,
          "isEligibleForUnemploymentBenefit": false
        }
        """;

    mockMvc
        .perform(
            post("/v1/scenarios/simulate").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.scenarioName").value("기본 시나리오"))
        .andExpect(jsonPath("$.data.type").value("ESTIMATED_OPPORTUNITY_COST"))
        .andExpect(jsonPath("$.data.feasibility.difficultyGrade").value("EASY"));
  }

  @Test
  @DisplayName("POST /v1/scenarios/simulate - 다양한 목표 연봉 구간 실현 가능성 검증")
  void simulateScenarios_feasibility_tiers() throws Exception {
    // 50,000,000 (<= 58M)
    mockMvc
        .perform(
            post("/v1/scenarios/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "stayExpectedRaiseRate": 3.0,
                      "moveTargetGrossSalary": 50000000,
                      "moveOneTimeCost": 0,
                      "expectedUnemploymentMonths": 0,
                      "isEligibleForUnemploymentBenefit": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.feasibility.difficultyGrade").value("EASY"));

    // 60,000,000 (<= 65M)
    mockMvc
        .perform(
            post("/v1/scenarios/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "stayExpectedRaiseRate": 3.0,
                      "moveTargetGrossSalary": 60000000,
                      "moveOneTimeCost": 0,
                      "expectedUnemploymentMonths": 0,
                      "isEligibleForUnemploymentBenefit": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.feasibility.difficultyGrade").value("REALISTIC"));

    // 70,000,000 (<= 74M)
    mockMvc
        .perform(
            post("/v1/scenarios/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "stayExpectedRaiseRate": 3.0,
                      "moveTargetGrossSalary": 70000000,
                      "moveOneTimeCost": 0,
                      "expectedUnemploymentMonths": 0,
                      "isEligibleForUnemploymentBenefit": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.feasibility.difficultyGrade").value("CHALLENGING"));

    // 120,000,000 (> 95M)
    mockMvc
        .perform(
            post("/v1/scenarios/simulate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "stayExpectedRaiseRate": 3.0,
                      "moveTargetGrossSalary": 120000000,
                      "moveOneTimeCost": 0,
                      "expectedUnemploymentMonths": 0,
                      "isEligibleForUnemploymentBenefit": false
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.feasibility.difficultyGrade").value("VERY_HIGH_CHALLENGE"));
  }

  @Test
  @DisplayName("GET /v1/scenarios/latest - 최근 시나리오 조회")
  void getLatestScenario() throws Exception {
    mockMvc
        .perform(get("/v1/scenarios/latest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.scenarioName").value("A사 오퍼 제안"))
        .andExpect(jsonPath("$.data.result").exists());
  }
}
