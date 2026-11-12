package com.carix.profile.dto;

import com.carix.profile.domain.RetirementPensionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "커리어 프로필 부분 변경 요청")
public record ProfileRequest(
    // 길이 제한은 deploy/postgres/schema/02_user_domain.sql 의 career_profile 컬럼 너비와 맞춘다.
    // 허용값 자체(enum)는 검증하지 않는다 — 별도 제품 결정이다.
    @Size(max = 100) @Schema(example = "DEVELOPER", nullable = true) String jobFamily,
    @Min(0) @Schema(example = "5", nullable = true) Integer yearsOfExperience,
    @Size(max = 100) @Schema(example = "SEOUL_GANGNAM", nullable = true) String region,
    @Size(max = 50) @Schema(example = "FULL_TIME", nullable = true) String employmentType,
    @Size(max = 50) @Schema(example = "MEDIUM", nullable = true) String companySize,
    @Schema(example = "2022-03-02", nullable = true) LocalDate hireDate,
    @Schema(example = "DC", nullable = true) RetirementPensionType retirementPensionType) {}
