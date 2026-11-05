package com.carix.dashboard;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.common.usercontext.CurrentUserId;
import com.carix.dashboard.dto.DashboardRecalculateResponse;
import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.SalaryDistributionResponse;
import com.carix.dashboard.dto.SkillDemandListResponse;
import com.carix.dashboard.dto.SkillDemandListResponse.SkillDemandDetail;
import com.carix.dashboard.dto.SkillDemandListResponse.SkillSourceStats;
import com.carix.dashboard.dto.SnapshotHistoryResponse;
import com.carix.dashboard.dto.TrendListResponse;
import com.carix.dashboard.dto.WageGrowthResponse;
import com.carix.dashboard.service.DashboardSummaryService;
import com.carix.dashboard.service.SalaryDistributionService;
import com.carix.dashboard.service.SnapshotSeriesService;
import com.carix.dashboard.service.WageGrowthService;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.index.mvi.MarketValueIndexServiceImpl;
import com.carix.index.mvi.MarketValueIndexServiceImpl.SkillDemandDetailData;
import com.carix.snapshot.SnapshotRecalculationService;
import com.carix.snapshot.YearMonths;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대시보드 및 지표 관련 API 컨트롤러.
 *
 * <p>도메인 분업 현황 (개발 계획 §1):
 *
 * <ul>
 *   <li><b>Member B (스킬 & 시장가치):</b> {@code /skills}, {@code /salary-distribution}
 *   <li><b>Member C (화면 셸 & 대시보드 종합):</b> {@code /summary}, {@code /trends}, {@code /history}
 * </ul>
 */
@RestController
@RequestMapping("/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard & Indices", description = "지표 요약, 트렌드, 동료 비교 분포, 스킬 수요, 스냅샷 목록 API")
public class DashboardController {

  private final MarketValueIndexServiceImpl marketValueIndexService;
  private final SalaryDistributionService salaryDistributionService;
  private final DashboardSummaryService dashboardSummaryService;
  private final SnapshotSeriesService snapshotSeriesService;
  private final SnapshotRecalculationService snapshotRecalculationService;
  private final IncomeHistoryRepository incomeHistoryRepository;
  private final WageGrowthService wageGrowthService;

  // =========================================================================
  // 1. [Member C 소유] 대시보드 지표 요약 (S-10)
  // =========================================================================
  @Operation(
      summary = "대시보드 지표 요약",
      description =
          "구매력 지수, 시장가치 지수, 결정 신호 및 표본 정보 등의 대시보드 요약 정보를 조회합니다. 가상 프로필 탐색을 위한 필터 파라미터를 입력할 수 있습니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @GetMapping("/summary")
  public ResponseEntity<ApiResult<DashboardSummaryResponse>> getDashboardSummary(
      @CurrentUserId UUID userId,
      @Parameter(description = "인덱스 기준 월 (YYYYMM 또는 YYYY-MM, 기본: 현재월)")
          @RequestParam(required = false)
          String baseYearMonth,
      @Parameter(description = "MVP 범위 밖 — 받기만 하고 쓰지 않는다 (개발 계획 §4)")
          @RequestParam(required = false)
          String jobFamily,
      @Parameter(description = "MVP 범위 밖 — 받기만 하고 쓰지 않는다 (개발 계획 §4)")
          @RequestParam(required = false)
          Integer yearsOfExperience,
      @Parameter(description = "MVP 범위 밖 — 받기만 하고 쓰지 않는다 (개발 계획 §4)")
          @RequestParam(required = false)
          String region,
      @Parameter(description = "MVP 범위 밖 — 받기만 하고 쓰지 않는다 (개발 계획 §4)")
          @RequestParam(required = false)
          String companySize) {

    if (baseYearMonth == null || baseYearMonth.isBlank()) {
      // 재계산은 데이터를 바꿀 때만 돌아서, 월이 바뀌면 현재월 스냅샷이 없다 — 조회 시점에 채운다.
      snapshotRecalculationService.ensureCurrentMonth(userId);
    }
    return ResponseEntity.ok(
        ApiResult.success(
            dashboardSummaryService.resolve(userId, normalizeYearMonth(baseYearMonth))));
  }

  // =========================================================================
  // 2. [Member B 소유] 보유 스킬별 상세 수요 정보 (S-14 스킬수요 탭)
  // =========================================================================
  @Operation(
      summary = "보유 스킬별 상세 수요 정보 조회",
      description = "로그인 사용자가 등록한 스킬별 최근 수요 지수, 가중치 및 원천별 상세 통계치를 제공합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @GetMapping("/skills")
  public ResponseEntity<ApiResult<SkillDemandListResponse>> getSkillDemands(
      @CurrentUserId UUID userId,
      @Parameter(description = "인덱스 기준 월 (YYYYMM 또는 YYYY-MM, 기본: 202607)", example = "202607")
          @RequestParam(required = false)
          String baseYearMonth) {

    String targetYm = baseYearMonth != null ? baseYearMonth.replace("-", "") : "202607";
    List<SkillDemandDetailData> details =
        marketValueIndexService.getSkillDemandDetails(userId, targetYm);

    if (details.isEmpty()) {
      return ResponseEntity.ok(
          ApiResult.success(new SkillDemandListResponse(List.of(), "등록된 보유 스킬이 없습니다.")));
    }

    String coverageText = buildCoverageSummary(details);
    List<SkillDemandDetail> skillResponses =
        details.stream().map(this::toSkillDemandDetailDto).toList();

    return ResponseEntity.ok(
        ApiResult.success(new SkillDemandListResponse(skillResponses, coverageText)));
  }

  // =========================================================================
  // 3. [Member B 소유] 동료집단 연봉 분포 5수치 (S-14 연봉분포 탭)
  // =========================================================================
  @Operation(
      summary = "동료집단 연봉 분포",
      description = "임금통계 기준 중위값과 채용공고의 연봉 백분위수 구간 및 본인의 위치 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "조회 성공"),
    @ApiResponse(
        responseCode = "401",
        description = "인증 에러",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/salary-distribution")
  public ResponseEntity<ApiResult<SalaryDistributionResponse>> getSalaryDistribution(
      @CurrentUserId UUID userId,
      @Parameter(description = "인덱스 기준 월 (YYYYMM 또는 YYYY-MM, 기본: 현재월)")
          @RequestParam(required = false)
          String baseYearMonth,
      @Parameter(description = "경력 사다리 기준 연도 (YYYY, 미지정 시 최신 연도)") @RequestParam(required = false)
          String year,
      @Parameter(description = "MVP 범위 밖 — 받기만 하고 쓰지 않는다 (개발 계획 §4)")
          @RequestParam(required = false)
          String jobFamily,
      @Parameter(description = "MVP 범위 밖 — 받기만 하고 쓰지 않는다 (개발 계획 §4)")
          @RequestParam(required = false)
          Integer yearsOfExperience,
      @Parameter(description = "MVP 범위 밖 — 받기만 하고 쓰지 않는다 (개발 계획 §4)")
          @RequestParam(required = false)
          String region,
      @Parameter(description = "MVP 범위 밖 — 받기만 하고 쓰지 않는다 (개발 계획 §4)")
          @RequestParam(required = false)
          String companySize) {

    return ResponseEntity.ok(
        ApiResult.success(
            year != null && !year.isBlank()
                ? salaryDistributionService.resolve(userId, normalizeYearMonth(baseYearMonth), year)
                : salaryDistributionService.resolve(userId, normalizeYearMonth(baseYearMonth))));
  }

  // =========================================================================
  // 4. [Member C 소유] 지수 트렌드 차트 데이터 (S-14 추이분석 셸)
  // =========================================================================
  @Operation(
      summary = "지수 트렌드 차트 데이터",
      description = "소득 지수, 물가 지수 및 구매력/시장가치 지수의 시계열 월별 차트용 데이터를 조회합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @GetMapping("/trends")
  public ResponseEntity<ApiResult<TrendListResponse>> getTrends(
      @CurrentUserId UUID userId,
      @Parameter(description = "인덱스 기준 월 (YYYYMM 또는 YYYY-MM, 기본: 현재월)")
          @RequestParam(required = false)
          String baseYearMonth,
      @Parameter(description = "조회할 개월 수 (기본 12)", example = "12")
          @RequestParam(required = false, defaultValue = "12")
          Integer durationMonths) {

    return ResponseEntity.ok(
        ApiResult.success(
            snapshotSeriesService.trends(
                userId, normalizeYearMonth(baseYearMonth), durationMonths)));
  }

  // =========================================================================
  // 4-1. [Member C 소유] 상승률 비교 (S-14, US-D8)
  // =========================================================================
  @Operation(
      summary = "상승률 비교",
      description = "내 연봉과 같은 경력 구간 임금 중앙값의 누적 상승을 연도별로 비교합니다. 세션 사용자 본인 값만 반환합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @GetMapping("/wage-growth")
  public ResponseEntity<ApiResult<WageGrowthResponse>> getWageGrowth(@CurrentUserId UUID userId) {
    return ResponseEntity.ok(ApiResult.success(wageGrowthService.resolve(userId)));
  }

  // =========================================================================
  // 5. [Member C 소유 API / 화면은 Member B (S-11)] 월별 스냅샷 이력
  // =========================================================================
  @Operation(summary = "월별 지수 스냅샷 이력", description = "과거 월별 기준 지수값 및 결정 신호 등의 스냅샷 목록을 최신순으로 가져옵니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @GetMapping("/history")
  public ResponseEntity<ApiResult<SnapshotHistoryResponse>> getSnapshotHistory(
      @CurrentUserId UUID userId) {
    return ResponseEntity.ok(ApiResult.success(snapshotSeriesService.history(userId)));
  }

  // =========================================================================
  // 6. [Member C 소유] 대시보드 스냅샷 재계산 (트리거)
  // =========================================================================
  @Operation(
      summary = "스냅샷 재계산 트리거",
      description = "지정된 기준월(또는 최초 소득 등록월)부터 현재월까지의 스냅샷을 재계산하고 최신 요약 정보를 반환합니다.")
  @ApiResponse(responseCode = "200", description = "재계산 및 요약 조회 성공")
  @PostMapping("/recalculate")
  public ResponseEntity<ApiResult<DashboardRecalculateResponse>> recalculate(
      @CurrentUserId UUID userId,
      @Parameter(description = "재계산 시작 기준월 (YYYYMM 또는 YYYY-MM, 미지정 시 최초 소득 등록월)")
          @RequestParam(required = false)
          String fromYearMonth) {

    String targetFrom = resolveFromYearMonth(userId, fromYearMonth);
    int recalculatedMonths = snapshotRecalculationService.recalculateSnapshot(userId, targetFrom);
    DashboardSummaryResponse summary =
        dashboardSummaryService.resolve(userId, normalizeYearMonth(null));

    return ResponseEntity.ok(
        ApiResult.success(
            new DashboardRecalculateResponse(recalculatedMonths, targetFrom, summary)));
  }

  private String resolveFromYearMonth(UUID userId, String fromYearMonth) {
    if (fromYearMonth != null && !fromYearMonth.isBlank()) {
      return normalizeYearMonth(fromYearMonth);
    }
    List<IncomeHistory> incomes =
        incomeHistoryRepository.findByUserIdOrderByEffectiveDateDesc(userId);
    if (!incomes.isEmpty()) {
      return incomes
          .get(incomes.size() - 1)
          .getEffectiveDate()
          .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
    }
    return YearMonths.current();
  }

  // =========================================================================
  // Private Helper Methods
  // =========================================================================
  /** {@code YYYY-MM} 과 {@code YYYYMM} 을 모두 받아 {@code YYYYMM} 으로 맞춘다. 없으면 현재월이다. */
  private String normalizeYearMonth(String baseYearMonth) {
    if (baseYearMonth == null || baseYearMonth.isBlank()) {
      // 스냅샷 현재월과 같은 UTC 기준이어야 한다. 서버 시간대를 따르면 월초에 방금 만든 달을 못 찾는다.
      return YearMonths.current();
    }
    return baseYearMonth.replace("-", "");
  }

  private SkillDemandDetail toSkillDemandDetailDto(SkillDemandDetailData data) {
    List<SkillSourceStats> sourceStats =
        data.getSources().stream()
            .map(s -> new SkillSourceStats(s.source(), s.demandIndex(), s.sampleSize()))
            .toList();

    return new SkillDemandDetail(
        data.getSkillId(),
        data.getStandardName(),
        data.getDemandIndex(),
        data.getRecentDemandRate(),
        data.getBaselineDemandRate(),
        sourceStats,
        data.getWeight(),
        data.getStatus());
  }

  private String buildCoverageSummary(List<SkillDemandDetailData> details) {
    long validCount =
        details.stream().filter(d -> !"CUSTOM_SKILL_NO_STATS".equals(d.getStatus())).count();
    return String.format("보유 스킬 %d개 중 %d개 반영 (가중치 100%% 재정규화)", details.size(), validCount);
  }
}
