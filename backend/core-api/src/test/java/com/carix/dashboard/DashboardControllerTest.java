package com.carix.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.GlobalExceptionHandler;
import com.carix.common.usercontext.UserContextArgumentResolver;
import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.SalaryDistributionResponse;
import com.carix.dashboard.service.DashboardSummaryService;
import com.carix.dashboard.service.SalaryDistributionService;
import com.carix.dashboard.service.SnapshotSeriesService;
import com.carix.dashboard.service.WageGrowthService;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.mvi.MarketValueIndexServiceImpl;
import com.carix.index.mvi.MarketValueIndexServiceImpl.SkillDemandDetailData;
import com.carix.index.mvi.MarketValueIndexServiceImpl.SkillSourceStatData;
import com.carix.snapshot.SnapshotRangeResolver;
import com.carix.snapshot.SnapshotRecalculationService;
import com.carix.snapshot.YearMonths;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
  private WageGrowthService wageGrowthService;

  @BeforeEach
  void setUp() {
    marketValueIndexService = mock(MarketValueIndexServiceImpl.class);
    salaryDistributionService = mock(SalaryDistributionService.class);
    dashboardSummaryService = mock(DashboardSummaryService.class);
    snapshotSeriesService = mock(SnapshotSeriesService.class);
    snapshotRecalculationService = mock(SnapshotRecalculationService.class);
    incomeHistoryRepository = mock(IncomeHistoryRepository.class);
    wageGrowthService = mock(WageGrowthService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new DashboardController(
                    marketValueIndexService,
                    salaryDistributionService,
                    dashboardSummaryService,
                    snapshotSeriesService,
                    snapshotRecalculationService,
                    new SnapshotRangeResolver(incomeHistoryRepository),
                    wageGrowthService,
                    mock(IndexSnapshotRepository.class)))
            .setCustomArgumentResolvers(new UserContextArgumentResolver())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
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
                    45_000_000L, 58_000_000L, 65_000_000L, 74_000_000L, 95_000_000L, 41, 42.5, 30),
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
                new DashboardSummaryResponse.DecisionSignal(
                    "HOLD", "유지 신호", List.of(), "2.0.0", null),
                null,
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
                new DashboardSummaryResponse.DecisionSignal(
                    "HOLD", "유지 신호", List.of(), "2.0.0", null),
                null,
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

  // QA 리뷰: fromYearMonth 를 검증 없이 넘겨, 형식이 틀린 값은 파싱 예외로 500 이 났다.
  @Test
  @DisplayName("POST /v1/dashboard/recalculate - 시작월 형식이 틀리면 재계산하지 않고 400 INVALID_INPUT")
  void recalculate_malformedFrom_rejected() throws Exception {
    for (String malformed : List.of("202613", "2026-00", "2026/03", "abc", "20260")) {
      mockMvc
          .perform(
              post("/v1/dashboard/recalculate")
                  .header("X-User-Id", USER_ID.toString())
                  .param("fromYearMonth", malformed))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
    verify(snapshotRecalculationService, never()).recalculateSnapshot(any(), any());
  }

  // QA 리뷰: fromYearMonth=000101 이면 한 사용자가 약 24,000 개월을 락을 쥔 채 한 트랜잭션에서 계산했다.
  @Test
  @DisplayName("POST /v1/dashboard/recalculate - 최초 소득월보다 이른 시작월은 최초 소득월로 올린다")
  void recalculate_fromBeforeEarliestIncome_raisedToEarliest() throws Exception {
    givenEarliestIncome(LocalDate.of(2025, 3, 15));

    mockMvc
        .perform(
            post("/v1/dashboard/recalculate")
                .header("X-User-Id", USER_ID.toString())
                .param("fromYearMonth", "000101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fromYearMonth").value("202503"));
    verify(snapshotRecalculationService).recalculateSnapshot(USER_ID, "202503");
  }

  // QA 리뷰: 현재월보다 늦은 시작월도 그대로 넘어갔다. 현재월로 묶는다.
  @Test
  @DisplayName("POST /v1/dashboard/recalculate - 현재월보다 늦은 시작월은 현재월로 묶는다")
  void recalculate_futureFrom_clampedToCurrent() throws Exception {
    givenEarliestIncome(LocalDate.of(2025, 3, 15));

    mockMvc
        .perform(
            post("/v1/dashboard/recalculate")
                .header("X-User-Id", USER_ID.toString())
                .param("fromYearMonth", "9999-12"))
        .andExpect(status().isOk());
    verify(snapshotRecalculationService).recalculateSnapshot(USER_ID, YearMonths.current());
  }

  // 범위를 묶다가 범위 안의 정상 요청까지 최초 소득월로 끌어내리면 안 된다.
  @Test
  @DisplayName("POST /v1/dashboard/recalculate - 범위 안의 시작월은 그대로 쓴다")
  void recalculate_inRangeFrom_passedThrough() throws Exception {
    givenEarliestIncome(LocalDate.of(2025, 3, 15));

    mockMvc
        .perform(
            post("/v1/dashboard/recalculate")
                .header("X-User-Id", USER_ID.toString())
                .param("fromYearMonth", "2025-06"))
        .andExpect(status().isOk());
    verify(snapshotRecalculationService).recalculateSnapshot(USER_ID, "202506");
  }

  private void givenEarliestIncome(LocalDate effectiveDate) {
    IncomeHistory recent =
        new IncomeHistory(USER_ID, 60000000L, 48000000L, LocalDate.of(2026, 1, 1), null);
    IncomeHistory earliest = new IncomeHistory(USER_ID, 50000000L, 40000000L, effectiveDate, null);
    given(incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(List.of(recent, earliest));
  }

  // 2026-11-01 운영: 월이 바뀌자 현재월 스냅샷이 없어 대시보드가 "첫 지수를 계산하고 있습니다" 에 멈췄다.
  @Test
  @DisplayName("GET /v1/dashboard/summary - 기준월 미지정이면 현재월 스냅샷을 먼저 보장하고 요약한다")
  void summary_ensuresCurrentMonthBeforeResolving() throws Exception {
    mockMvc
        .perform(get("/v1/dashboard/summary").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk());

    InOrder order = inOrder(snapshotRecalculationService, dashboardSummaryService);
    order.verify(snapshotRecalculationService).ensureCurrentMonth(USER_ID);
    order.verify(dashboardSummaryService).resolve(USER_ID, YearMonths.current());
  }

  @Test
  @DisplayName("GET /v1/dashboard/wage-growth - 세션 사용자만 조회하고 쿼리의 userId 를 무시한다")
  void getWageGrowth_ignoresUserIdParameter() throws Exception {
    UUID other = UUID.randomUUID();

    mockMvc
        .perform(
            get("/v1/dashboard/wage-growth")
                .header("X-User-Id", USER_ID.toString())
                .param("userId", other.toString()))
        .andExpect(status().isOk());

    verify(wageGrowthService).resolve(USER_ID);
    verify(wageGrowthService, never()).resolve(other);
  }
}
