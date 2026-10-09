package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "월별 지수 스냅샷 이력 응답")
public record SnapshotHistoryResponse(List<SnapshotPoint> snapshots) {

  /** 스냅샷 한 행. 그 달에 지수를 계산하지 못했으면 {@code null} 로 남는다 ({@code index_snapshot} DDL 주석). */
  public record SnapshotPoint(
      String baseYearMonth,
      @Schema(nullable = true) Double purchasingPowerIndex,
      @Schema(nullable = true) Double marketValueIndex,
      @Schema(nullable = true) Double openingWindowIndex,
      String decisionSignal,
      @Schema(example = "스킬 보강 우선") String label,
      String dataVersion,
      String ruleVersion,
      SnapshotRationale rationale,
      @Schema(nullable = true) RecordedSnapshot recorded,
      boolean changed) {}

  /** 그 달 끝에 대시보드가 보여 줬을 판정. {@code recordedAt} 은 그 행이 만들어진 시각이다. */
  public record RecordedSnapshot(
      @Schema(nullable = true) Double purchasingPowerIndex,
      @Schema(nullable = true) Double marketValueIndex,
      @Schema(nullable = true) Double openingWindowIndex,
      String decisionSignal,
      String label,
      String dataVersion,
      String ruleVersion,
      SnapshotRationale rationale,
      OffsetDateTime recordedAt) {}

  public record SnapshotRationale(
      List<String> rulesApplied, List<String> excluded, LocalDate baseDate) {}
}
