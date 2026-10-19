package com.carix.dashboard;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.mvi.MarketValueIndexServiceImpl;
import com.carix.index.mvi.MarketValueIndexServiceImpl.SkillDemandDetailData;
import com.carix.index.mvi.MarketValueIndexServiceImpl.SkillSourceStatData;
import com.carix.snapshot.SnapshotRecalculationService;
import com.carix.snapshot.YearMonths;
import java.time.LocalDate;
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
  private SnapshotRecalculationService snapshotRecalculationService;
  private IncomeHistoryRepository incomeHistoryRepository;

  @BeforeEach
  void setUp() {
    marketValueIndexService = mock(MarketValueIndexServiceImpl.class);
    salaryDistributionService = mock(SalaryDistributionService.class);
    dashboardSummaryService = mock(DashboardSummaryService.class);
    snapshotSeriesService = mock(SnapshotSeriesService.class);
    snapshotRecalculationService = mock(SnapshotRecalculationService.class);
    incomeHistoryRepository = mock(IncomeHistoryRepository.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new DashboardController(
                    marketValueIndexService,
                    salaryDistributionService,
                    dashboardSummaryService,
                    snapshotSeriesService,
                    snapshotRecalculationService,
                    incomeHistoryRepository))
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
                unavailable,
                null,
                new DashboardSummaryResponse.DecisionSignal(
                    "INSUFFICIENT_DATA", "데이터 부족", List.of(), null),
                new DashboardSummaryResponse.SummaryMetadata(
                    null, null, null, false, List.of(), null, null, null, null, null, null, null)));

    mockMvc
        .perform(
            get("/v1/dashboard/summary")
                .header("X-User-Id", USER_ID.toString())
                .param("baseYearMonth", "202607"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.purchasingPowerIndex.value").value(nullValue()))
        .andExpect(jsonPath("$.data.purchasingPowerIndex.indexStatus").value("NOT_AVAILABLE"))
        .andExpect(jsonPath("$.data.openingWindowIndex.indexStatus").value("NOT_AVAILABLE"))
        .andExpect(jsonPath("$.data.metadata.openingWindow").value(nullValue()))
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
                new SalaryDistributionResponse.DistributionPeerGroup(3, "서울 / 경력 MID / 회사규모 무관"),
                List.of(
                    new SalaryDistributionResponse.SalaryTrendPoint(
                        "2026-05", 62_000_000L, 18, 11, 38.9),
                    new SalaryDistributionResponse.SalaryTrendPoint("2026-06", null, 6, 2, 66.7),
                    new SalaryDistributionResponse.SalaryTrendPoint(
                        "2026-07", 64_000_000L, 22, 14, 36.4))));

    mockMvc
        .perform(
            get("/v1/dashboard/salary-distribution")
                .header("X-User-Id", USER_ID.toString())
                .param("baseYearMonth", "2026-07"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("OK"))
        .andExpect(jsonPath("$.data.postingReference.median").value(65000000L))
        .andExpect(jsonPath("$.data.peerGroup.fallbackLevel").value(3))
        .andExpect(jsonPath("$.data.trend[0].yearMonth").value("2026-05"))
        .andExpect(jsonPath("$.data.trend[0].median").value(62000000L))
        .andExpect(jsonPath("$.data.trend[1].yearMonth").value("2026-06"))
        .andExpect(jsonPath("$.data.trend[1].median").isEmpty());
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
  @DisplayName("GET /v1/dashboard/salary-distribution - year 파라미터가 있으면 3-arg resolve 를 호출한다 (b14)")
  void getSalaryDistribution_연도파라미터() throws Exception {
    given(salaryDistributionService.resolve(eq(USER_ID), eq("202607"), eq("2024")))
        .willReturn(
            new SalaryDistributionResponse(
                "OK",
                50,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                new SalaryDistributionResponse.CareerLadder(
                    "MID",
                    "2024",
                    List.of("2023", "2024", "2025"),
                    List.of(
                        new SalaryDistributionResponse.CareerLadderStep(
                            "ENTRY", 32_000_000L, null, "KOSIS"),
                        new SalaryDistributionResponse.CareerLadderStep(
                            "JUNIOR", 42_000_000L, 31.25, "KOSIS")))));

    mockMvc
        .perform(
            get("/v1/dashboard/salary-distribution")
                .header("X-User-Id", USER_ID.toString())
                .param("baseYearMonth", "2026-07")
                .param("year", "2024"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("OK"))
        .andExpect(jsonPath("$.data.careerLadder.basePeriod").value("2024"))
        .andExpect(jsonPath("$.data.careerLadder.availableYears[1]").value("2024"))
        .andExpect(jsonPath("$.data.careerLadder.steps[0].bracket").value("ENTRY"));
  }

  @Test
  @DisplayName("GET /v1/dashboard/trends - 계열 상태를 함께 싣는다")
  void getTrends() throws Exception {
    given(snapshotSeriesService.trends(eq(USER_ID), eq("202601"), eq(6)))
        .willReturn(
            new TrendListResponse(List.of(), "NOT_AVAILABLE", "NOT_AVAILABLE", "NOT_AVAILABLE"));

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

  @Test
  @DisplayName("POST /v1/dashboard/recalculate - 명시적 기준월이 주어지면 해당 월부터 재계산한다")
  void recalculate_withExplicitFromYearMonth() throws Exception {
    given(snapshotRecalculationService.recalculateSnapshot(USER_ID, "202603")).willReturn(5);
    DashboardSummaryResponse.IndexDetail detail =
        new DashboardSummaryResponse.IndexDetail(105.0, 5.0, "INCREASED", "OK", null);
    DashboardSummaryResponse summary =
        new DashboardSummaryResponse(
            detail,
            detail,
            detail,
            null,
            new DashboardSummaryResponse.DecisionSignal("SIGNAL_BUY", "매수 신호", List.of(), "2.0.0"),
            new DashboardSummaryResponse.SummaryMetadata(
                LocalDate.of(2026, 7, 31),
                "2026-07-31T00:00:00Z",
                "v1",
                true,
                List.of(),
                100,
                50000000.0,
                null,
                null,
                null,
                null,
                null));
    given(dashboardSummaryService.resolve(eq(USER_ID), any())).willReturn(summary);

    mockMvc
        .perform(
            post("/v1/dashboard/recalculate")
                .header("X-User-Id", USER_ID.toString())
                .param("fromYearMonth", "2026-03"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recalculatedMonths").value(5))
        .andExpect(jsonPath("$.data.fromYearMonth").value("202603"))
        .andExpect(jsonPath("$.data.summary.decisionSignal.code").value("SIGNAL_BUY"));
  }

  @Test
  @DisplayName("POST /v1/dashboard/recalculate - 기준월 미지정 시 소득 이력의 가장 이른 월부터 재계산한다")
  void recalculate_withEarliestIncomeHistory() throws Exception {
    IncomeHistory recent =
        new IncomeHistory(USER_ID, 60000000L, 48000000L, LocalDate.of(2026, 1, 1), null);
    IncomeHistory earliest =
        new IncomeHistory(USER_ID, 50000000L, 40000000L, LocalDate.of(2025, 3, 15), null);
    given(incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(List.of(recent, earliest));
    given(snapshotRecalculationService.recalculateSnapshot(USER_ID, "202503")).willReturn(17);
    DashboardSummaryResponse.IndexDetail detail =
        new DashboardSummaryResponse.IndexDetail(100.0, 0.0, "SAME", "OK", null);
    given(dashboardSummaryService.resolve(eq(USER_ID), any()))
        .willReturn(
            new DashboardSummaryResponse(
                detail,
                detail,
                detail,
                null,
                new DashboardSummaryResponse.DecisionSignal("HOLD", "유지 신호", List.of(), "2.0.0"),
                new DashboardSummaryResponse.SummaryMetadata(
                    LocalDate.of(2026, 7, 31),
                    "2026-07-31T00:00:00Z",
                    "v1",
                    true,
                    List.of(),
                    100,
                    50000000.0,
                    null,
                    null,
                    null,
                    null,
                    null)));

    mockMvc
        .perform(post("/v1/dashboard/recalculate").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recalculatedMonths").value(17))
        .andExpect(jsonPath("$.data.fromYearMonth").value("202503"));
  }

  @Test
  @DisplayName("POST /v1/dashboard/recalculate - 기준월 미지정 및 소득 이력 없을 시 현재월로 재계산한다")
  void recalculate_noIncomeHistory_defaultsToCurrent() throws Exception {
    given(incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(List.of());
    String currentYm = YearMonths.current();
    given(snapshotRecalculationService.recalculateSnapshot(USER_ID, currentYm)).willReturn(1);
    DashboardSummaryResponse.IndexDetail detail =
        new DashboardSummaryResponse.IndexDetail(100.0, 0.0, "SAME", "OK", null);
    given(dashboardSummaryService.resolve(eq(USER_ID), any()))
        .willReturn(
            new DashboardSummaryResponse(
                detail,
                detail,
                detail,
                null,
                new DashboardSummaryResponse.DecisionSignal("HOLD", "유지 신호", List.of(), "2.0.0"),
                new DashboardSummaryResponse.SummaryMetadata(
                    LocalDate.now(),
                    "2026-07-31T00:00:00Z",
                    "v1",
                    true,
                    List.of(),
                    100,
                    50000000.0,
                    null,
                    null,
                    null,
                    null,
                    null)));

    mockMvc
        .perform(post("/v1/dashboard/recalculate").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.recalculatedMonths").value(1))
        .andExpect(jsonPath("$.data.fromYearMonth").value(currentYm));
  }
}
