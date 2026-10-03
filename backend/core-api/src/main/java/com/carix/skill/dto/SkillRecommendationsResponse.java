package com.carix.skill.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 동료집단 기반 스킬 보강 추천 응답 DTO (API 규격 §5.6, 기능 명세 US-D6).
 *
 * @param peerGroup 적용된 동료집단 완화 조건 정보
 * @param recommendations 추천 스킬 목록 (수요율 상위 최대 3개)
 */
public record SkillRecommendationsResponse(
    PeerGroupInfo peerGroup, List<SkillRecommendationItem> recommendations) {

  public record PeerGroupInfo(String appliedConditions) {}

  public record SkillRecommendationItem(
      String standardName,
      String category,
      Double demandRate,
      String growthTrend,
      CoOccurrenceInfo coOccurrence,
      String reason) {}

  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record CoOccurrenceInfo(String withSkill, Double rate) {}
}
