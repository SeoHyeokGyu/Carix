package com.carix.collector.dto;

public record BackfillRequest(Boolean dryRun // 기본값 true (안전한 시뮬레이션 기본)
    ) {
  public BackfillRequest {
    if (dryRun == null) {
      dryRun = true;
    }
  }
}
