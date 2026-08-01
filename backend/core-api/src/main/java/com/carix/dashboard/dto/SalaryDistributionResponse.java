package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동료집단 연봉 분포 응답")
public record SalaryDistributionResponse(
    WageBaseline baseline,
    PostingReference postingReference,
    UserPosition userPosition,
    DistributionPeerGroup peerGroup
) {

  public record WageBaseline(String source, long median, String wageDefinition) {

  }

  public record PostingReference(long min, long p25, long median, long p75, long max,
                                 int sampleSize, double missingRate) {

  }

  public record UserPosition(long grossSalary, int percentile) {

  }

  public record DistributionPeerGroup(int fallbackLevel, String appliedConditions) {

  }
}
