package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "월별 지수 스냅샷 이력 응답")
public record SnapshotHistoryResponse(List<SnapshotPoint> snapshots) {

  /** 스냅샷 한 행. 그 달에 지수를 계산하지 못했으면 {@code null} 로 남는다 ({@code index_snapshot} DDL 주석). */
  public record SnapshotPoint(
      String baseYearMonth,
      @Schema(nullable = true) Double purchasingPowerIndex,
      @Schema(nullable = true) Double marketValueIndex,
      String decisionSignal,
      @Schema(example = "스킬 보강 우선") String label,
      String dataVersion,
      String ruleVersion,
      SnapshotRationale rationale) {}

  public record SnapshotRationale(
      List<String> rulesApplied, List<String> excluded, LocalDate baseDate) {}
}
