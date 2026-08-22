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

  public record IndexDetail(
      @Schema(example = "94.2") double value,
      @Schema(example = "-1.2") double changeFromLastMonth,
      @Schema(example = "DECREASED") String status,
      @Schema(description = "가중치 추정 상태 라벨", example = "PARTIALLY_ESTIMATED", nullable = true)
          String estimateLabel) {}

  public record DecisionSignal(
      @Schema(example = "SEEK_JOB") String code,
      @Schema(example = "이직 탐색") String label,
      @Schema(example = "[\"연봉 격차 15% 이상\"]") List<String> rulesApplied,
      @Schema(example = "보유 중인 스킬의 가치는...") String rationale) {}

  public record SummaryMetadata(
      LocalDate baseDate,
      String dataVersion,
      String ruleVersion,
      boolean updateDelayed,
      List<DataLimitSource> sources,
      int sampleSize,
      double salaryMissingRate,
      PeerGroup peerGroup,
      WageStatInfo wageStat,
      String expenseWeightStatus) {}

  public record DataLimitSource(String source, int sampleSize) {}

  public record PeerGroup(
      int fallbackLevel, String appliedConditions, List<ExcludedSkill> excluded) {}

  public record ExcludedSkill(String skill, String reason) {}

  public record WageStatInfo(String wageDefinition, String statVintage) {}
}
