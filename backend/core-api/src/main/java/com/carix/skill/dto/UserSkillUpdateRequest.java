package com.carix.skill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "보유 스킬 수정 요청")
public record UserSkillUpdateRequest(
    @Min(1) @Max(600) @Schema(description = "경험 개월 수 (1~600)", example = "24")
        int monthsOfExperience,
    @NotNull @Schema(description = "마지막 사용일", example = "2026-07-01") LocalDate lastUsedDate) {}
