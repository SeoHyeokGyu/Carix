package com.carix.skill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "동료집단 기반 추천 스킬 응답")
public record SkillRecommendationsResponse(
        PeerGroupDetail peerGroup,
        List<RecommendedSkillDetail> recommendations
) {
    public record PeerGroupDetail(
            @Schema(example = "서울 전체 / 경력 MID / 회사규모 무관") String appliedConditions
    ) {}

    public record RecommendedSkillDetail(
            @Schema(example = "Kubernetes") String standardName,
            @Schema(example = "DEVOPS") String category,
            @Schema(example = "0.245") double demandRate,
            @Schema(example = "UP") String growthTrend,
            @Schema(example = "동료집단 내 공고의 24.5%에서 요구하고 있으며...") String reason
    ) {}
}
