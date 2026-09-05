package com.carix.skill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import java.time.LocalDate;

@Schema(description = "보유 스킬 등록 요청")
public record UserSkillCreateRequest(
    @NotBlank @Schema(description = "표준 스킬명 또는 커스텀 스킬명", example = "Spring Boot")
        String standardName,
    @Min(1) @Max(600) @Schema(description = "경험 개월 수 (1~600)", example = "24")
        int monthsOfExperience,
    @NotNull
        @PastOrPresent(message = "마지막 사용일은 현재 또는 과거 일자여야 합니다.")
        @Schema(description = "마지막 사용일", example = "2026-07-01")
        LocalDate lastUsedDate,
    @Schema(
            description = "커스텀 스킬일 경우 필수 (BACKEND, FRONTEND, DEVOPS, DATA, AI_ML, ETC)",
            example = "BACKEND",
            nullable = true)
        String category) {}
