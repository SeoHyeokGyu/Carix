package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "월별 지수 스냅샷 이력 응답")
public record SnapshotHistoryResponse(List<SnapshotPoint> snapshots) {

  public record SnapshotPoint(
      String baseYearMonth,
      double purchasingPowerIndex,
      double marketValueIndex,
      String decisionSignal,
      String dataVersion,
      String ruleVersion,
      SnapshotRationale rationale) {}

  public record SnapshotRationale(
      List<String> rulesApplied, List<String> excluded, LocalDate baseDate) {}
}
