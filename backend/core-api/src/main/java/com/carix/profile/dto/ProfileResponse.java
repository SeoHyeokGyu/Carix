package com.carix.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "커리어 프로필 응답")
public record ProfileResponse(
    @Schema(example = "DEVELOPER") String jobFamily,
    @Schema(example = "5", nullable = true) Integer yearsOfExperience,
    @Schema(example = "SEOUL_GANGNAM") String region,
    @Schema(example = "FULL_TIME") String employmentType,
    @Schema(example = "MEDIUM") String companySize,
    @Schema(example = "2022-03-02", nullable = true) LocalDate hireDate) {}
