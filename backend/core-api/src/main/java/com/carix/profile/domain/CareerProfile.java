package com.carix.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 커리어 프로필. 온보딩 자동 저장을 허용하려고 전 컬럼이 NULL 허용이다 — 필수 필드 완비 검증은 온보딩 완료 제출 시 애플리케이션에서 한다 (DDL 주석, {@code
 * deploy/postgres/schema/02_user_domain.sql}).
 */
@Entity
@Table(name = "career_profile", schema = "core")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CareerProfile {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "job_family", length = 100)
  private String jobFamily;

  @Column(name = "years_of_experience")
  private Integer yearsOfExperience;

  @Column(name = "region", length = 100)
  private String region;

  @Column(name = "employment_type", length = 50)
  private String employmentType;

  @Column(name = "company_size", length = 50)
  private String companySize;

  public CareerProfile(UUID userId) {
    this.userId = userId;
  }

  /** {@code null} 인자는 "변경하지 않음" 이다. 온보딩 화면이 단계별로 일부 필드만 보내기 때문이다. */
  public void patch(
      String jobFamily,
      Integer yearsOfExperience,
      String region,
      String employmentType,
      String companySize) {
    if (jobFamily != null) {
      this.jobFamily = jobFamily;
    }
    if (yearsOfExperience != null) {
      this.yearsOfExperience = yearsOfExperience;
    }
    if (region != null) {
      this.region = region;
    }
    if (employmentType != null) {
      this.employmentType = employmentType;
    }
    if (companySize != null) {
      this.companySize = companySize;
    }
  }
}
