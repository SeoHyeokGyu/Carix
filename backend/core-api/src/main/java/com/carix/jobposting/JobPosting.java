package com.carix.jobposting;

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
 * 원 채용공고 (core.job_posting) — core-api 는 읽기만 한다.
 *
 * <p>수집·중복제거·집계는 collector 소유이고 이 매핑은 조회 전용이다. {@code market_aggregate} 를 core-api 에도 매핑해 둔 것과 같은
 * 이유로 엔티티가 두 모듈에 있다.
 *
 * <p>이 매핑이 필요한 이유는 하나다 — [API 규격 §5.2] 의 동료집단 연봉 분포는 결합 분포와 백분위를 요구하는데, 원천별로 5수치만 저장하는 {@code
 * market_aggregate} 에서는 재구성할 수 없다. 컬럼은 그 집계에 쓰는 것만 담았다.
 */
@Entity
@Table(schema = "core", name = "job_posting")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobPosting {

  @Id private UUID id;

  /** 원천 간 중복 묶음. {@code null} 이면 묶이지 않은 단독 공고다. */
  @Column(name = "dedup_group_id")
  private UUID dedupGroupId;

  /** 공개 연봉 하한. 상·하한이 모두 있어야 유효 공개연봉으로 센다. */
  @Column(name = "salary_min")
  private Long salaryMin;

  @Column(name = "salary_max")
  private Long salaryMax;

  @Column(name = "region")
  private String region;

  @Column(name = "experience_min")
  private Integer experienceMin;

  @Column(name = "experience_max")
  private Integer experienceMax;

  @Column(name = "employment_type", length = 100)
  private String employmentType;

  @Column(name = "company_size", length = 50)
  private String companySize;

  @Column(name = "job_family", length = 100)
  private String jobFamily;

  /**
   * 유효 공개연봉의 대표값. 상·하한의 중간값이다 (collector {@code MarketAggregateService.calculateSalaryStats} 와 같은
   * 규약).
   *
   * @return 상·하한 중 하나라도 없으면 {@code null}
   */
  public Long disclosedSalary() {
    if (salaryMin == null || salaryMax == null) {
      return null;
    }
    return (salaryMin + salaryMax) / 2;
  }

  /** 중복 제거 키. 묶음이 없으면 자기 자신이 하나의 묶음이다. */
  public UUID dedupKey() {
    return dedupGroupId != null ? dedupGroupId : id;
  }
}
