package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "대시보드 지표 요약 응답")
public record DashboardSummaryResponse(
    IndexDetail purchasingPowerIndex,
    IndexDetail marketValueIndex,
    DecisionSignal decisionSignal,
    SummaryMetadata metadata) {

  /**
   * 지수 한 건. 계산 불가면 {@code value} 와 {@code changeFromLastMonth} 가 {@code null} 이고 사유는 {@code
   * indexStatus} 에 담긴다 (개발 계획 §4-1 — 0 이나 100 으로 채우지 않는다).
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
   * {@code index_snapshot.rationale.sourceCoverages} 에서 온다 (개발 계획 §5-M — 둘 다 기록으로 결정됨). 그 달에 저장된
   * 커버리지가 없으면 비어 있고, <b>임의 값으로 채우지 않는다</b>.
   */
  public record SummaryMetadata(
      @Schema(nullable = true) LocalDate baseDate,
      @Schema(nullable = true) String dataVersion,
      @Schema(nullable = true) String ruleVersion,
      boolean updateDelayed,
      @Schema(description = "원천별 표본. §5-M 결정 전까지 빈 배열") List<DataLimitSource> sources,
      @Schema(description = "§5-M 미저장 — null", nullable = true) Integer sampleSize,
      @Schema(description = "§5-M 미저장 — null", nullable = true) Double salaryMissingRate,
      @Schema(description = "§5-M 미저장 — null", nullable = true) PeerGroup peerGroup,
      @Schema(nullable = true) WageStatInfo wageStat,
      @Schema(nullable = true) String expenseWeightStatus) {}

  public record DataLimitSource(String source, int sampleSize) {}

  public record PeerGroup(
      @Schema(nullable = true) Integer fallbackLevel,
      String appliedConditions,
      List<ExcludedSkill> excluded) {}

  public record ExcludedSkill(String skill, String reason) {}

  public record WageStatInfo(String wageDefinition, String statVintage) {}
}
