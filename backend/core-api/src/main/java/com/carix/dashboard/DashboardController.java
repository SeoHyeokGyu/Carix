package com.carix.dashboard;

import com.carix.common.ApiResult;
import com.carix.common.ErrorResponse;
import com.carix.dashboard.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/dashboard")
@Tag(name = "Dashboard & Indices", description = "지표 요약, 트렌드, 동료 비교 분포, 스냅샷 목록 API")
public class DashboardController {

    @Operation(summary = "대시보드 지표 요약", description = "구매력 지수, 시장가치 지수, 결정 신호 및 표본 정보 등의 대시보드 요약 정보를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/summary")
    public ResponseEntity<ApiResult<DashboardSummaryResponse>> getDashboardSummary() {
        return ResponseEntity.ok(ApiResult.success(new DashboardSummaryResponse(
                new DashboardSummaryResponse.IndexDetail(94.2, -1.2, "DECREASED", "PARTIALLY_ESTIMATED"),
                new DashboardSummaryResponse.IndexDetail(117.5, 3.8, "INCREASED", null),
                new DashboardSummaryResponse.DecisionSignal("SEEK_JOB", "이직 탐색",
                        List.of("연봉 격차 15% 이상", "보유 스킬 시장수요 상승세"),
                        "보유 중인 스킬의 가치는 상승 추세이며, 현재 연봉 6,000만 원은 공식 임금통계 중앙값 7,200만 원 대비 격차 20%입니다."),
                new DashboardSummaryResponse.SummaryMetadata(
                        LocalDate.of(2026, 7, 1),
                        "20260701093000-000",
                        "1.2.0",
                        false,
                        List.of(new DashboardSummaryResponse.DataLimitSource("SARAMIN", 124)),
                        124,
                        42.5,
                        new DashboardSummaryResponse.PeerGroup(3, "서울 전체 / 경력 MID / 회사규모 무관",
                                List.of(new DashboardSummaryResponse.ExcludedSkill("CustomSkillX", "CUSTOM_SKILL_NO_STATS"))),
                        new DashboardSummaryResponse.WageStatInfo("초과급여 제외, 상여 포함", "202606"),
                        "PARTIALLY_ESTIMATED"
                )
        )));
    }

    @Operation(summary = "동료집단 연봉 분포", description = "임금통계 기준 중위값과 채용공고의 연봉 백분위수 구간 및 본인의 위치 정보를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 에러", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/salary-distribution")
    public ResponseEntity<ApiResult<SalaryDistributionResponse>> getSalaryDistribution() {
        return ResponseEntity.ok(ApiResult.success(new SalaryDistributionResponse(
                new SalaryDistributionResponse.WageBaseline("WAGE_STAT", 72000000L, "초과급여 제외, 상여 포함"),
                new SalaryDistributionResponse.PostingReference(45000000L, 58000000L, 65000000L, 74000000L, 95000000L, 71, 42.5),
                new SalaryDistributionResponse.UserPosition(60000000L, 35),
                new SalaryDistributionResponse.DistributionPeerGroup(3, "서울 전체 / 경력 MID / 회사규모 무관")
        )));
    }

    @Operation(summary = "지수 트렌드 차트 데이터", description = "소득 지수, 물가 지수 및 구매력/시장가치 지수의 시계열 월별 차트용 데이터를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/trends")
    public ResponseEntity<ApiResult<TrendListResponse>> getTrends(
            @Parameter(description = "인덱스 기준 월 (YYYY-MM)", example = "2026-01") @RequestParam(required = false) String baseYearMonth,
            @Parameter(description = "조회할 개월 수 (기본 12)", example = "12") @RequestParam(required = false, defaultValue = "12") Integer durationMonths
    ) {
        return ResponseEntity.ok(ApiResult.success(new TrendListResponse(List.of(
                new TrendListResponse.TrendPoint("2026-01", 100.0, 100.0, 100.0, 100.0, 5000000L, 5000000L),
                new TrendListResponse.TrendPoint("2026-02", 99.1, 102.3, 100.0, 100.9, 5000000L, 4955000L)
        ))));
    }

    @Operation(summary = "보유 스킬별 상세 수요 정보 조회", description = "사용자가 추가한 스킬별 최근 수요 지수 및 원천별 상세 통계치를 제공합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/skills")
    public ResponseEntity<ApiResult<SkillDemandListResponse>> getSkillDemands() {
        return ResponseEntity.ok(ApiResult.success(new SkillDemandListResponse(
                List.of(
                        new SkillDemandListResponse.SkillDemandDetail(
                                UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa7"), "Spring Boot", 123.4, 0.0812, 0.0658,
                                List.of(
                                        new SkillDemandListResponse.SkillSourceStats("SARAMIN", 123.4, 124)
                                ),
                                0.715, "OK"
                        ),
                        new SkillDemandListResponse.SkillDemandDetail(
                                UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa8"), "CustomSkillX", 0.0, 0.0, 0.0,
                                List.of(), 0.0, "CUSTOM_SKILL_NO_STATS"
                        )
                ),
                "보유 스킬 2개 중 1개 반영 (가중치 100% 재정규화)"
        )));
    }

    @Operation(summary = "월별 지수 스냅샷 이력", description = "과거 월별 기준 지수값 및 결정 신호 등의 스냅샷 목록을 최신순으로 가져옵니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/history")
    public ResponseEntity<ApiResult<SnapshotHistoryResponse>> getSnapshotHistory() {
        return ResponseEntity.ok(ApiResult.success(new SnapshotHistoryResponse(List.of(
                new SnapshotHistoryResponse.SnapshotPoint(
                        "202607", 94.2, 117.5, "SEEK_JOB", "20260701093000-000", "1.2.0",
                        new SnapshotHistoryResponse.SnapshotRationale(List.of("..."), List.of("..."), LocalDate.of(2026, 7, 1))
                )
        ))));
    }

    @Operation(summary = "가상 소비/소득 변화에 따른 지수 예측 시뮬레이션", description = "예상되는 가상의 연봉 인상률 및 지출 절감액을 적용하여 미래 구매력 지수 예측 변동치를 계산합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "예측 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 입력값", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/predict")
    public ResponseEntity<ApiResult<IndexPredictionResponse>> predictIndices(@Valid @RequestBody IndexPredictionRequest request) {
        double currentVal = 94.2;
        double futureVal = currentVal + (request.futureExpectedRaiseRate() * 0.8) - (request.futureMonthlyExpenseChange() / 100000.0 * 0.9);
        double diff = futureVal - currentVal;
        String status = diff >= 0 ? "INCREASED" : "DECREASED";

        String rationale = String.format("연봉이 %.1f%% 인상되고 월 지출을 %,d원 %s하면 실질 가처분 소득이 변화하여, 구매력 지수가 현재 %.1f에서 %.1f로 약 %.1fp %s할 것으로 추정됩니다.",
                request.futureExpectedRaiseRate(),
                Math.abs(request.futureMonthlyExpenseChange()),
                request.futureMonthlyExpenseChange() < 0 ? "절감" : "증가",
                currentVal,
                futureVal,
                Math.abs(diff),
                diff >= 0 ? "상승" : "하락");

        return ResponseEntity.ok(ApiResult.success(new IndexPredictionResponse(
                currentVal,
                futureVal,
                diff,
                status,
                rationale
        )));
    }
}
