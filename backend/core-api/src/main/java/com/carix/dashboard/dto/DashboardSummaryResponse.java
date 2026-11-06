package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "대시보드 지표 요약 응답")
public record DashboardSummaryResponse(
    IndexDetail purchasingPowerIndex,
    IndexDetail marketValueIndex,
    IndexDetail openingWindowIndex,
    @Schema(description = "협상 목표 인상률. NEGOTIATE·SEEK_JOB 의 가장 최근 달에만", nullable = true)
        NegotiationTargets negotiationTargets,
    DecisionSignal decisionSignal,
    @Schema(description = "성과급 포함 연간 실소득. 기준월 포함 24개월 창에 지급월이 있는 성과급이 없으면 null", nullable = true)
        BonusInclusiveIncome bonusInclusiveIncome,
    SummaryMetadata metadata) {

  /**
   * 지수 한 건. 계산 불가면 {@code value} 와 {@code changeFromLastMonth} 가 {@code null} 이고 사유는 {@code
   * indexStatus} 에 담긴다 (API 규격 §5.1 — 0 이나 100 으로 채우지 않는다).
   */
  public record IndexDetail(
      @Schema(example = "94.2", nullable = true) Double value,
      @Schema(example = "-1.2", nullable = true) Double changeFromLastMonth,
      @Schema(description = "전월 대비 방향", example = "DECREASED", nullable = true) String status,
      @Schema(
              description = "산출 상태 (com.carix.index.enums.IndexStatus)",
              example = "OK",
              allowableValues = {
                "OK",
                "INSUFFICIENT_SAMPLE",
                "UPDATE_DELAYED",
                "INSUFFICIENT_HISTORY",
                "NOT_AVAILABLE"
              })
          String indexStatus,
      @Schema(description = "가중치 추정 상태 라벨", example = "PARTIALLY_ESTIMATED", nullable = true)
          String estimateLabel) {}

  public record DecisionSignal(
      @Schema(example = "SEEK_JOB") String code,
      @Schema(example = "이직 탐색") String label,
      @Schema(example = "[\"연봉 격차 15% 이상\"]") List<String> rulesApplied,
      @Schema(example = "보유 중인 스킬의 가치는...") String rationale,
      @Schema(
              description = "공고 비중 변화 문장. 판정 조건이 아닌 관측 사실이며, 표시 조건을 못 넘으면 null",
              example = "2026년 6~8월 내 동료집단 공고 비중이 이전 12개월보다 늘었습니다 (22.6% → 23.8%).",
              nullable = true)
          String timingEvidence) {}

  /**
   * 지수 산출의 근거·기준일 묶음.
   *
   * <p>{@code sources}·{@code sampleSize}·{@code salaryMissingRate}·{@code peerGroup} 은 원천별 커버리지이고
   * {@code index_snapshot.rationale.sourceCoverages} 에서 온다 (기술 명세 §1.3 — 둘 다 기록으로 결정됨). 그 달에 저장된
   * 커버리지가 없으면 비어 있고, <b>임의 값으로 채우지 않는다</b>.
   */
  public record SummaryMetadata(
      @Schema(nullable = true) LocalDate baseDate,
      @Schema(nullable = true) String dataVersion,
      @Schema(nullable = true) String ruleVersion,
      boolean updateDelayed,
      @Schema(description = "원천별 완화 단계·표본 수. 저장된 커버리지가 없으면 빈 배열") List<DataLimitSource> sources,
      @Schema(description = "표본 수 가중 합계. 완화에 성공한 원천이 없으면 null", nullable = true) Integer sampleSize,
      @Schema(description = "원천별 결측률의 표본 수 가중 평균. 없으면 null", nullable = true)
          Double salaryMissingRate,
      @Schema(description = "표본이 가장 큰 원천의 안착 조건. 없으면 null", nullable = true) PeerGroup peerGroup,
      @Schema(nullable = true) WageStatInfo wageStat,
      @Schema(nullable = true) String expenseWeightStatus,
      @Schema(description = "개방창 지수 근거 셋. 근거가 없으면 null", nullable = true)
          OpeningWindowSummary openingWindow,
      @Schema(description = "물가 기여도 1위 카테고리 (US-D1). 산출 불가면 null", nullable = true)
          PriceDriver topPriceDriver) {}

  /**
   * 그 사용자의 물가 상승에 가장 크게 기여한 카테고리 (API 규격 §5.1). 저장된 기여도 값은 내보내지 않는다 — 쓰는 화면이 없다.
   *
   * @param estimated 그 카테고리 가중치가 기본값으로 채워졌는지
   * @param cpiMonth 사용한 CPI 기준월 {@code YYYYMM}
   */
  public record PriceDriver(
      @Schema(example = "TRANSPORT") String category,
      @Schema(example = "교통 부담률 최대 상승") String label,
      boolean estimated,
      @Schema(example = "202609") String cpiMonth) {}

  /**
   * 최근 12개월과 직전 12개월의 소득(월할 연봉·고정상여 + 지급 성과급) 비교. 금액은 12개월 합계이고 지수에는 섞지 않는다.
   *
   * <p>소득 금액은 싣지 않는다 — 연봉 복원 경로를 만들지 않는다.
   *
   * @param bonusCount 최근 12개월에 지급된 성과급 건수
   * @param realChangePercent 개인물가로 디플레이트한 실질 변화(%). 직전 12개월 창을 소득 행이 온전히 덮지 못하거나 개인물가가 없으면 null
   */
  public record BonusInclusiveIncome(
      @Schema(example = "3.1", nullable = true) Double realChangePercent, int bonusCount) {}

  public record DataLimitSource(String source, int sampleSize) {}

  public record PeerGroup(
      @Schema(nullable = true) Integer fallbackLevel,
      String appliedConditions,
      List<ExcludedSkill> excluded) {}

  public record ExcludedSkill(String skill, String reason) {}

  /**
   * 협상 목표 인상률 셋 (기능 명세 US-D7·§4.8). 비율만 싣는다 — 목표 금액은 같은 응답의 기준선과 조인하면 연봉이 복원돼 싣지 않고, 어디에도 저장하지 않는다.
   * 항목 {@code null} 은 근거 없음이다.
   */
  public record NegotiationTargets(
      @Schema(nullable = true) Target toWageStatMedian,
      @Schema(nullable = true) Target toPeerP75,
      @Schema(nullable = true) RecoveryTarget toPurchasingPowerRecovery) {}

  public record Target(
      @Schema(example = "20.0", nullable = true) Double raiseRatePercent,
      @Schema(allowableValues = {"BELOW", "ABOVE"}) String status) {}

  /** 마지막 연봉 변경 이후 개인물가 상승분. {@code NONE} 은 상승분이 없다는 뜻이다. */
  public record RecoveryTarget(
      @Schema(example = "4.3", nullable = true) Double raiseRatePercent,
      @Schema(allowableValues = {"BELOW", "NONE"}) String status,
      @Schema(description = "실제로 누적을 시작한 달", example = "202503") String sinceYearMonth) {}

  public record WageStatInfo(
      @Schema(example = "ANNUAL_GROSS_MEDIAN") String wageDefinition,
      @Schema(example = "202606") String statVintage,
      @Schema(example = "AGE_BAND_PROXY") String basis,
      @Schema(example = "KOSIS") String source) {

    public WageStatInfo(String wageDefinition, String statVintage) {
      this(wageDefinition, statVintage, "AGE_BAND_PROXY", "UNKNOWN");
    }
  }

  /**
   * 채용 활발도(OWI)의 화면용 근거 (API 규격 §5.1, b22). 저장된 근거 중 셋만 그대로 나간다 — 시점 문장의 재료는 {@code
   * DecisionSignal.timingEvidence} 로 접혀 나간다.
   *
   * <p>{@code recentSharePercent}·{@code baselineSharePercent} 는 퍼센트(0.00 ~ 100.00)다.
   */
  public record OpeningWindowSummary(
      @Schema(description = "최근 3개월 동료집단 채용 비중(%)", example = "24.5", nullable = true)
          Double recentSharePercent,
      @Schema(description = "이전 12개월 동료집단 채용 비중(%)", example = "20.8", nullable = true)
          Double baselineSharePercent,
      @Schema(description = "최근 3개월 동료집단 표본 수", example = "86", nullable = true)
          Integer peerSampleSize,
      @Schema(
              description = "최근 창의 첫 달. 유효월 기준이라 기준월보다 이를 수 있다",
              example = "202606",
              nullable = true)
          String recentFromMonth,
      @Schema(description = "최근 창의 마지막 달", example = "202608", nullable = true)
          String recentToMonth) {}
}
