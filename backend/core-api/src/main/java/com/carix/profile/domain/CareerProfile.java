package com.carix.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 커리어 프로필 엔티티 (core.career_profile).
 *
 * <p>사용자의 직무, 총 경력 년수, 근무 지역, 고용 형태, 회사 규모 정보를 보관합니다.
 */
@Entity
@Table(schema = "core", name = "career_profile")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CareerProfile {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "job_family", length = 100)
  private String jobFamily;

  @Column(name = "years_of_experience")
  private Integer yearsOfExperience;

  @Column(length = 100)
  private String region;

  @Column(name = "employment_type", length = 50)
  private String employmentType;

  @Column(name = "company_size", length = 50)
  private String companySize;
}
