package com.carix.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.common.usercontext.UserContextArgumentResolver;
import com.carix.dashboard.service.DashboardSummaryService;
import com.carix.dashboard.service.SalaryDistributionService;
import com.carix.dashboard.service.SnapshotSeriesService;
import com.carix.dashboard.service.WageGrowthService;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.mvi.MarketValueIndexServiceImpl;
import com.carix.snapshot.SnapshotRecalculationService;
import com.carix.snapshot.YearMonths;
import com.carix.snapshot.domain.IndexSnapshot;
import com.carix.snapshot.repository.IndexSnapshotRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SkillDemandBaseMonthTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private MarketValueIndexServiceImpl marketValueIndexService;
  private IndexSnapshotRepository indexSnapshotRepository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    marketValueIndexService = mock(MarketValueIndexServiceImpl.class);
    indexSnapshotRepository = mock(IndexSnapshotRepository.class);
    given(marketValueIndexService.getSkillDemandDetails(any(), any())).willReturn(List.of());
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new DashboardController(
                    marketValueIndexService,
                    mock(SalaryDistributionService.class),
                    mock(DashboardSummaryService.class),
                    mock(SnapshotSeriesService.class),
                    mock(SnapshotRecalculationService.class),
                    mock(IncomeHistoryRepository.class),
                    mock(WageGrowthService.class),
                    indexSnapshotRepository))
            .setCustomArgumentResolvers(new UserContextArgumentResolver())
            .build();
  }

  // 회귀: 기준월을 202607 로 고정해 두어 스킬 화면과 대시보드(최신 스냅샷 달)의 시장가치가 서로 달랐다.
  @Test
  void usesLatestSnapshotMonthWhenBaseYearMonthOmitted() throws Exception {
    given(indexSnapshotRepository.findFirstByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(Optional.of(IndexSnapshot.builder().baseYearMonth("202609").build()));

    mockMvc
        .perform(get("/v1/dashboard/skills").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk());

    verify(marketValueIndexService).getSkillDemandDetails(USER_ID, "202609");
  }

  @Test
  void fallsBackToCurrentMonthWhenNoSnapshot() throws Exception {
    given(indexSnapshotRepository.findFirstByUserIdOrderByBaseYearMonthDesc(USER_ID))
        .willReturn(Optional.empty());

    mockMvc
        .perform(get("/v1/dashboard/skills").header("X-User-Id", USER_ID.toString()))
        .andExpect(status().isOk());

    verify(marketValueIndexService).getSkillDemandDetails(USER_ID, YearMonths.current());
  }
}
