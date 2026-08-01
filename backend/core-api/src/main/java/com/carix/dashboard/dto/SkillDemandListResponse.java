package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "보유 스킬별 상세 수요 정보 응답")
public record SkillDemandListResponse(List<SkillDemandDetail> skills, String coverage) {

  public record SkillDemandDetail(
      UUID skillId,
      String standardName,
      double demandIndex,
      double recentDemandRate,
      double baselineDemandRate,
      List<SkillSourceStats> sources,
      double weight,
      String status
  ) {

  }

  public record SkillSourceStats(String source, double demandIndex, int sampleSize) {

  }
}
