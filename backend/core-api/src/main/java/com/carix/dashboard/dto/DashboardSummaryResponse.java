package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "대시보드 지표 요약 응답")
public record DashboardSummaryResponse(
    IndexDetail purchasingPowerIndex,
    IndexDetail marketValueIndex,
    IndexDetail openingWindowIndex,
    DecisionSignal decisionSignal,
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
      @Schema(example = "보유 중인 스킬의 가치는...") String rationale) {}

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

  public record DataLimitSource(String source, int sampleSize) {}

  public record PeerGroup(
      @Schema(nullable = true) Integer fallbackLevel,
      String appliedConditions,
      List<ExcludedSkill> excluded) {}

  public record ExcludedSkill(String skill, String reason) {}

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
   * 개방창 지수의 화면용 근거 (API 규격 §5.1). 저장된 근거 일곱 중 셋만 나간다 — 나머지 넷은 쓰는 화면이 아직 없다.
   *
   * <p>{@code openEndedRate} 는 퍼센트다. 내부 {@code OpeningWindowEvidence.openEndedRatePercent} 와 같은 값이고
   * 이름만 규격을 따른다.
   */
  public record OpeningWindowSummary(
      @Schema(example = "34", nullable = true) Integer medianWindowDays,
      @Schema(description = "상시채용 비율(%)", example = "21.5", nullable = true) Double openEndedRate,
      @Schema(example = "86", nullable = true) Integer deadlineSampleSize) {}
}
