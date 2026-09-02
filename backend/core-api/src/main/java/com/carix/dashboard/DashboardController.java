package com.carix.dashboard;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.common.usercontext.CurrentUserId;
import com.carix.dashboard.dto.DashboardSummaryResponse;
import com.carix.dashboard.dto.SalaryDistributionResponse;
import com.carix.dashboard.dto.SkillDemandListResponse;
import com.carix.dashboard.dto.SkillDemandListResponse.SkillDemandDetail;
import com.carix.dashboard.dto.SkillDemandListResponse.SkillSourceStats;
import com.carix.dashboard.dto.SnapshotHistoryResponse;
import com.carix.dashboard.dto.TrendListResponse;
import com.carix.index.mvi.MarketValueIndexServiceImpl;
import com.carix.index.mvi.MarketValueIndexServiceImpl.SkillDemandDetailData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
      @Parameter(description = "가상 탐색용 주 직무", example = "DEVELOPER") @RequestParam(required = false)
          String jobFamily,
      @Parameter(description = "가상 탐색용 경력 년수", example = "5") @RequestParam(required = false)
          Integer yearsOfExperience,
      @Parameter(description = "가상 탐색용 지역구", example = "SEOUL_GANGNAM")
          @RequestParam(required = false)
          String region,
      @Parameter(description = "가상 탐색용 회사 규모", example = "MEDIUM") @RequestParam(required = false)
          String companySize) {

    // TODO: [Member C 3~4주차] Snapshot 및 Decision Signal 엔진 연동 실데이터 전환
    double purchasingPower = 94.2;
    double marketValue = 117.5;
    String decisionCode = "SEEK_JOB";
    String decisionLabel = "이직 탐색";
    String bracket = "MID";

    if (yearsOfExperience != null) {
      if (yearsOfExperience >= 8) {
        purchasingPower = 120.4;
        marketValue = 145.8;
        decisionCode = "NEGOTIATE";
        decisionLabel = "연봉 협상";
        bracket = "SENIOR";
      } else if (yearsOfExperience <= 3) {
        purchasingPower = 81.5;
        marketValue = 92.1;
        decisionCode = "STAY";
        decisionLabel = "잔류 권장";
        bracket = "JUNIOR";
      }
    }

    String jobLabel = jobFamily != null ? jobFamily : "DEVELOPER";
    String regionLabel = region != null ? region : "서울 전체";
    String sizeLabel = companySize != null ? companySize : "회사규모 무관";

    return ResponseEntity.ok(
        ApiResult.success(
            new DashboardSummaryResponse(
                new DashboardSummaryResponse.IndexDetail(
                    purchasingPower, -1.2, "DECREASED", "PARTIALLY_ESTIMATED"),
                new DashboardSummaryResponse.IndexDetail(marketValue, 3.8, "INCREASED", null),
                new DashboardSummaryResponse.DecisionSignal(
                    decisionCode,
                    decisionLabel,
                    List.of("연봉 격차 분석 기준", "보유 스킬 시장수요 추세 반영"),
                    String.format(
                        "가상 탐색(%s, %s, %s) 결과 기준 신호입니다.", jobLabel, regionLabel, sizeLabel)),
                new DashboardSummaryResponse.SummaryMetadata(
                    LocalDate.of(2026, 7, 1),
                    "20260701093000-000",
                    "1.2.0",
                    false,
                    List.of(new DashboardSummaryResponse.DataLimitSource("SARAMIN", 124)),
                    124,
                    42.5,
                    new DashboardSummaryResponse.PeerGroup(
                        3,
                        String.format("%s / 경력 %s / %s", regionLabel, bracket, sizeLabel),
                        List.of(
                            new DashboardSummaryResponse.ExcludedSkill(
                                "CustomSkillX", "CUSTOM_SKILL_NO_STATS"))),
                    new DashboardSummaryResponse.WageStatInfo("초과급여 제외, 상여 포함", "202606"),
                    "PARTIALLY_ESTIMATED"))));
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
  // TODO: [Member B 3~4주차] job_posting 직접 집계 기반 실데이터 연동 구현 (개발 계획 §5-J)
  @GetMapping("/salary-distribution")
  public ResponseEntity<ApiResult<SalaryDistributionResponse>> getSalaryDistribution(
      @Parameter(description = "가상 탐색용 주 직무", example = "DEVELOPER") @RequestParam(required = false)
          String jobFamily,
      @Parameter(description = "가상 탐색용 경력 년수", example = "5") @RequestParam(required = false)
          Integer yearsOfExperience,
      @Parameter(description = "가상 탐색용 지역구", example = "SEOUL_GANGNAM")
          @RequestParam(required = false)
          String region,
      @Parameter(description = "가상 탐색용 회사 규모", example = "MEDIUM") @RequestParam(required = false)
          String companySize) {

    long baselineMedian = 72000000L;
    long min = 45000000L;
    long p25 = 58000000L;
    long median = 65000000L;
    long p75 = 74000000L;
    long max = 95000000L;
    long userSalary = 60000000L;
    int percentile = 35;
    String bracket = "MID";

    if (yearsOfExperience != null) {
      if (yearsOfExperience >= 8) {
        baselineMedian = 95000000L;
        min = 60000000L;
        p25 = 80000000L;
        median = 90000000L;
        p75 = 110000000L;
        max = 150000000L;
        userSalary = 90000000L;
        percentile = 50;
        bracket = "SENIOR";
      } else if (yearsOfExperience <= 3) {
        baselineMedian = 48000000L;
        min = 30000000L;
        p25 = 40000000L;
        median = 45000000L;
        p75 = 52000000L;
        max = 70000000L;
        userSalary = 42000000L;
        percentile = 35;
        bracket = "JUNIOR";
      }
    }

    String regionLabel = region != null ? region : "서울 전체";
    String sizeLabel = companySize != null ? companySize : "회사규모 무관";

    return ResponseEntity.ok(
        ApiResult.success(
            new SalaryDistributionResponse(
                new SalaryDistributionResponse.WageBaseline(
                    "WAGE_STAT", baselineMedian, "초과급여 제외, 상여 포함"),
                new SalaryDistributionResponse.PostingReference(
                    min, p25, median, p75, max, 71, 42.5),
                new SalaryDistributionResponse.UserPosition(userSalary, percentile),
                new SalaryDistributionResponse.DistributionPeerGroup(
                    3, String.format("%s / 경력 %s / %s", regionLabel, bracket, sizeLabel)))));
  }

  // =========================================================================
  // 4. [Member C 소유] 지수 트렌드 차트 데이터 (S-14 추이분석 셸)
  // =========================================================================
  @Operation(
      summary = "지수 트렌드 차트 데이터",
      description = "소득 지수, 물가 지수 및 구매력/시장가치 지수의 시계열 월별 차트용 데이터를 조회합니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  // TODO: [Member C 5~6주차] A/B 지수 계열 합성 trends[] API 실데이터 연동 (개발 계획 §2)
  @GetMapping("/trends")
  public ResponseEntity<ApiResult<TrendListResponse>> getTrends(
      @Parameter(description = "인덱스 기준 월 (YYYY-MM)", example = "2026-01")
          @RequestParam(required = false)
          String baseYearMonth,
      @Parameter(description = "조회할 개월 수 (기본 12)", example = "12")
          @RequestParam(required = false, defaultValue = "12")
          Integer durationMonths) {

    return ResponseEntity.ok(
        ApiResult.success(
            new TrendListResponse(
                List.of(
                    new TrendListResponse.TrendPoint(
                        "2026-01", 100.0, 100.0, 100.0, 100.0, 5000000L, 5000000L),
                    new TrendListResponse.TrendPoint(
                        "2026-02", 99.1, 102.3, 100.0, 100.9, 5000000L, 4955000L)))));
  }

  // =========================================================================
  // 5. [Member C 소유 API / 화면은 Member B (S-11)] 월별 스냅샷 이력
  // =========================================================================
  @Operation(summary = "월별 지수 스냅샷 이력", description = "과거 월별 기준 지수값 및 결정 신호 등의 스냅샷 목록을 최신순으로 가져옵니다.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  // TODO: [Member C 1~2주차] core.index_snapshot 기반 실데이터 이력 조회 연동
  @GetMapping("/history")
  public ResponseEntity<ApiResult<SnapshotHistoryResponse>> getSnapshotHistory() {
    return ResponseEntity.ok(
        ApiResult.success(
            new SnapshotHistoryResponse(
                List.of(
                    new SnapshotHistoryResponse.SnapshotPoint(
                        "202607",
                        94.2,
                        117.5,
                        "SEEK_JOB",
                        "20260701093000-000",
                        "1.2.0",
                        new SnapshotHistoryResponse.SnapshotRationale(
                            List.of("..."), List.of("..."), LocalDate.of(2026, 7, 1)))))));
  }

  // =========================================================================
  // Private Helper Methods
  // =========================================================================
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
