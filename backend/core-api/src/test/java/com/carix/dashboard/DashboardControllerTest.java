package com.carix.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.usercontext.UserContextArgumentResolver;
import com.carix.index.mvi.MarketValueIndexServiceImpl;
import com.carix.index.mvi.MarketValueIndexServiceImpl.SkillDemandDetailData;
import com.carix.index.mvi.MarketValueIndexServiceImpl.SkillSourceStatData;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DashboardControllerTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private MockMvc mockMvc;
  private MarketValueIndexServiceImpl marketValueIndexService;

  @BeforeEach
  void setUp() {
    marketValueIndexService = mock(MarketValueIndexServiceImpl.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new DashboardController(marketValueIndexService))
            .setCustomArgumentResolvers(new UserContextArgumentResolver())
            .build();
  }

  @Test
  @DisplayName("GET /v1/dashboard/summary - 기본 파라미터로 요약 정보 조회")
  void getDashboardSummary_default() throws Exception {
    mockMvc
        .perform(get("/v1/dashboard/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.decisionSignal.code").value("SEEK_JOB"));
  }

  @Test
  @DisplayName("GET /v1/dashboard/summary - 시니어 경력 (>= 8년) 파라미터")
  void getDashboardSummary_senior() throws Exception {
    mockMvc
        .perform(
            get("/v1/dashboard/summary")
                .param("jobFamily", "DEVELOPER")
                .param("yearsOfExperience", "10")
                .param("region", "SEOUL_GANGNAM")
                .param("companySize", "LARGE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decisionSignal.code").value("NEGOTIATE"));
  }

  @Test
  @DisplayName("GET /v1/dashboard/summary - 주니어 경력 (<= 3년) 파라미터")
  void getDashboardSummary_junior() throws Exception {
    mockMvc
        .perform(get("/v1/dashboard/summary").param("yearsOfExperience", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.decisionSignal.code").value("STAY"));
  }

  @Test
  @DisplayName("GET /v1/dashboard/skills - 등록된 스킬이 없는 경우")
  void getSkillDemands_empty() throws Exception {
    given(marketValueIndexService.getSkillDemandDetails(any(UUID.class), eq("202607")))
        .willReturn(List.of());

    mockMvc
        .perform(get("/v1/dashboard/skills").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.coverage").value("등록된 보유 스킬이 없습니다."));
  }

  @Test
  @DisplayName("GET /v1/dashboard/skills - 스킬 목록 조회 성공")
  void getSkillDemands_withData() throws Exception {
    UUID skill1 = UUID.randomUUID();
    UUID skill2 = UUID.randomUUID();

    SkillDemandDetailData data1 =
        SkillDemandDetailData.builder()
            .skillId(skill1)
            .standardName("Spring Boot")
            .demandIndex(120.0)
            .recentDemandRate(0.4)
            .baselineDemandRate(0.3)
            .weight(0.7)
            .status("OK")
            .sources(List.of(new SkillSourceStatData("JOBKOREA", 120.0, 50)))
            .build();

    SkillDemandDetailData data2 =
        SkillDemandDetailData.builder()
            .skillId(skill2)
            .standardName("CustomLang")
            .demandIndex(0.0)
            .recentDemandRate(0.0)
            .baselineDemandRate(0.0)
            .weight(0.0)
            .status("CUSTOM_SKILL_NO_STATS")
            .sources(List.of())
            .build();

    given(marketValueIndexService.getSkillDemandDetails(any(UUID.class), eq("202608")))
        .willReturn(List.of(data1, data2));

    mockMvc
        .perform(
            get("/v1/dashboard/skills")
                .header("X-User-Id", USER_ID.toString())
                .param("baseYearMonth", "2026-08"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.skills[0].standardName").value("Spring Boot"))
        .andExpect(jsonPath("$.data.coverage").value("보유 스킬 2개 중 1개 반영 (가중치 100% 재정규화)"));
  }

  @Test
  @DisplayName("GET /v1/dashboard/salary-distribution - 기본 및 경력별 분포")
  void getSalaryDistribution() throws Exception {
    // 기본
    mockMvc
        .perform(get("/v1/dashboard/salary-distribution"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.peerGroup.appliedConditions").value("서울 전체 / 경력 MID / 회사규모 무관"));

    // 시니어
    mockMvc
        .perform(
            get("/v1/dashboard/salary-distribution")
                .param("jobFamily", "DEVELOPER")
                .param("yearsOfExperience", "9")
                .param("region", "SEOUL")
                .param("companySize", "MEDIUM"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userPosition.grossSalary").value(90000000L));

    // 주니어
    mockMvc
        .perform(get("/v1/dashboard/salary-distribution").param("yearsOfExperience", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userPosition.grossSalary").value(42000000L));
  }

  @Test
  @DisplayName("GET /v1/dashboard/trends - 트렌드 데이터 조회")
  void getTrends() throws Exception {
    mockMvc
        .perform(
            get("/v1/dashboard/trends")
                .param("baseYearMonth", "2026-01")
                .param("durationMonths", "6"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.trends").isArray());
  }

  @Test
  @DisplayName("GET /v1/dashboard/history - 스냅샷 이력 조회")
  void getSnapshotHistory() throws Exception {
    mockMvc
        .perform(get("/v1/dashboard/history"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.snapshots").isArray());
  }
}
