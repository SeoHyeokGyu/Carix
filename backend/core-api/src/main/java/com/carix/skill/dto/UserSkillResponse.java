package com.carix.skill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "사용자 보유 스킬 상세 응답")
public record UserSkillResponse(
        @Schema(description = "보유 스킬 등록 매핑 ID") UUID id,
        @Schema(description = "표준 스킬 ID") UUID skillId,
        @Schema(example = "Spring Boot") String standardName,
        @Schema(description = "사용자 정의 여부") boolean isCustom,
        @Schema(description = "실무 경험 개월 수", example = "24") int monthsOfExperience,
        @Schema(description = "마지막 사용일", example = "2026-07-01") LocalDate lastUsedDate
) {}
