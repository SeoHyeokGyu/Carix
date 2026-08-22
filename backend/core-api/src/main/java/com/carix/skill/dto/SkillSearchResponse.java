package com.carix.skill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "스킬 사전 검색 결과 응답")
public record SkillSearchResponse(
    UUID id,
    @Schema(example = "Spring Boot") String standardName,
    @Schema(description = "사용자 정의 여부") boolean isCustom) {}
