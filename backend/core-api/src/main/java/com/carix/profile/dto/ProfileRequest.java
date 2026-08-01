package com.carix.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

@Schema(description = "커리어 프로필 부분 변경 요청")
public record ProfileRequest(
    @Schema(example = "DEVELOPER", nullable = true) String jobFamily,
    @Min(0) @Schema(example = "5", nullable = true) Integer yearsOfExperience,
    @Schema(example = "SEOUL_GANGNAM", nullable = true) String region,
    @Schema(example = "FULL_TIME", nullable = true) String employmentType,
    @Schema(example = "MEDIUM", nullable = true) String companySize
) {

}
