package com.carix.dashboard;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.usercontext.UserContextArgumentResolver;
import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.SalaryDistributionResponse;
import com.carix.dashboard.dto.SnapshotHistoryResponse;
import com.carix.dashboard.dto.TrendListResponse;
import com.carix.dashboard.service.DashboardSummaryService;
import com.carix.dashboard.service.SalaryDistributionService;
import com.carix.dashboard.service.SnapshotSeriesService;
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
  private SalaryDistributionService salaryDistributionService;
  private DashboardSummaryService dashboardSummaryService;
  private SnapshotSeriesService snapshotSeriesService;

  @BeforeEach
  void setUp() {
    marketValueIndexService = mock(MarketValueIndexServiceImpl.class);
    salaryDistributionService = mock(SalaryDistributionService.class);
    dashboardSummaryService = mock(DashboardSummaryService.class);
    snapshotSeriesService = mock(SnapshotSeriesService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new DashboardController(
                    marketValueIndexService,
                    salaryDistributionService,
                    dashboardSummaryService,
                    snapshotSeriesService))
            .setCustomArgumentResolvers(new UserContextArgumentResolver())
            .build();
  }

  @Test
  @DisplayName("GET /v1/dashboard/summary - 스냅샷이 없으면 NOT_AVAILABLE 을 그대로 싣는다")
  void getDashboardSummary_비어있음() throws Exception {
    DashboardSummaryResponse.IndexDetail unavailable =
        new DashboardSummaryResponse.IndexDetail(null, null, null, "NOT_AVAILABLE", null);
    given(dashboardSummaryService.resolve(eq(USER_ID), eq("202607")))
        .willReturn(
            new DashboardSummaryResponse(
                unavailable,
                unavailable,
                new DashboardSummaryResponse.DecisionSignal(
                    "INSUFFICIENT_DATA", "데이터 부족", List.of(), null),
                new DashboardSummaryResponse.SummaryMetadata(
                    null, null, null, false, List.of(), null, null, null, null, null)));

    mockMvc
        .perform(
            get("/v1/dashboard/summary")
                .header("X-User-Id", USER_ID.toString())
                .param("baseYearMonth", "202607"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.purchasingPowerIndex.value").value(nullValue()))
        .andExpect(jsonPath("$.data.purchasingPowerIndex.indexStatus").value("NOT_AVAILABLE"))
        .andExpect(jsonPath("$.data.decisionSignal.code").value("INSUFFICIENT_DATA"));
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
  @DisplayName("GET /v1/dashboard/salary-distribution - 서비스 산출 결과를 그대로 싣는다")
  void getSalaryDistribution() throws Exception {
    given(salaryDistributionService.resolve(eq(USER_ID), eq("202607")))
        .willReturn(
            new SalaryDistributionResponse(
                "OK",
                71,
                null,
                new SalaryDistributionResponse.WageBaseline(
                    "WAGE_STAT", 72_000_000L, "초과급여 제외, 상여 포함"),
                new SalaryDistributionResponse.PostingReference(
                    45_000_000L, 58_000_000L, 65_000_000L, 74_000_000L, 95_000_000L, 41, 42.5),
                new SalaryDistributionResponse.UserPosition(60_000_000L, 35),
                new SalaryDistributionResponse.DistributionPeerGroup(3, "서울 / 경력 MID / 회사규모 무관")));

    mockMvc
        .perform(
            get("/v1/dashboard/salary-distribution")
                .header("X-User-Id", USER_ID.toString())
                .param("baseYearMonth", "2026-07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("OK"))
        .andExpect(jsonPath("$.data.postingReference.median").value(65000000L))
        .andExpect(jsonPath("$.data.peerGroup.fallbackLevel").value(3));
  }

  @Test
  @DisplayName("GET /v1/dashboard/salary-distribution - 표본 부족도 200 으로 상태와 표본 수를 싣는다")
  void getSalaryDistribution_표본부족() throws Exception {
    given(salaryDistributionService.resolve(eq(USER_ID), eq("202607")))
        .willReturn(
            new SalaryDistributionResponse("INSUFFICIENT_SAMPLE", 4, 10, null, null, null, null));

    mockMvc
        .perform(
            get("/v1/dashboard/salary-distribution")
                .header("X-User-Id", USER_ID.toString())
                .param("baseYearMonth", "202607"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("INSUFFICIENT_SAMPLE"))
        .andExpect(jsonPath("$.data.sampleSize").value(4))
        .andExpect(jsonPath("$.data.minRequired").value(10))
        .andExpect(jsonPath("$.data.postingReference").value(nullValue()))
        .andExpect(jsonPath("$.data.userPosition").value(nullValue()));
  }

  @Test
  @DisplayName("GET /v1/dashboard/trends - 계열 상태를 함께 싣는다")
  void getTrends() throws Exception {
    given(snapshotSeriesService.trends(eq(USER_ID), eq("202601"), eq(6)))
        .willReturn(new TrendListResponse(List.of(), "NOT_AVAILABLE", "NOT_AVAILABLE"));

    mockMvc
        .perform(
            get("/v1/dashboard/trends")
                .header("X-User-Id", USER_ID.toString())
                .param("baseYearMonth", "2026-01")
                .param("durationMonths", "6"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.trends").isArray())
        .andExpect(jsonPath("$.data.purchasingPowerStatus").value("NOT_AVAILABLE"));
  }

  @Test
  @DisplayName("GET /v1/dashboard/history - 스냅샷이 없으면 빈 배열이다")
  void getSnapshotHistory() throws Exception {
    given(snapshotSeriesService.history(USER_ID))
        .willReturn(new SnapshotHistoryResponse(List.of()));

    mockMvc
        .perform(get("/v1/dashboard/history").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.snapshots").isArray())
        .andExpect(jsonPath("$.data.snapshots").isEmpty());
  }
}
