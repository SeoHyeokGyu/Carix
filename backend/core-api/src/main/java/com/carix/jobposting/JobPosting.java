package com.carix.jobposting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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

  private static final DateTimeFormatter SAMPLED_MONTH_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMM");

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

  /** 수집 원천 (JOBKOREA, SARAMIN 등). */
  @Column(name = "source", length = 20)
  private String source;

  /** 무작위 표본으로 수집된 월 (YYYYMM). 표본 프레임이 아니면 {@code null}. */
  @Column(name = "sampled_month", length = 6)
  private String sampledMonth;

  @Column(name = "posted_start_date")
  private LocalDate postedStartDate;

  @Column(name = "posted_end_date")
  private LocalDate postedEndDate;

  /**
   * 표본 월을 {@link YearMonth} 로 푼다.
   *
   * @return {@code sampledMonth} 가 없거나 YYYYMM 형식이 아니면 {@code null}
   */
  public YearMonth sampledYearMonth() {
    if (sampledMonth == null) {
      return null;
    }
    try {
      return YearMonth.parse(sampledMonth, SAMPLED_MONTH_FORMAT);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /**
   * 모집 개방 기간 (일수). {@code posted_end_date - posted_start_date} (기능 명세 §4.5).
   *
   * @return 시작일 또는 마감일이 없거나, 마감일이 시작일보다 앞선 역전 공고이면 {@code null}
   */
  public Integer windowDays() {
    if (postedStartDate == null || postedEndDate == null || isReversed()) {
      return null;
    }
    return (int) ChronoUnit.DAYS.between(postedStartDate, postedEndDate);
  }

  /** 역전 공고 여부 (posted_end_date < posted_start_date). 원천 오류로 표본에서 제외된다. */
  public boolean isReversed() {
    return postedStartDate != null
        && postedEndDate != null
        && postedEndDate.isBefore(postedStartDate);
  }

  /** 마감일 없음(상시채용·채용시 등). */
  public boolean isOpenEnded() {
    return postedStartDate != null && postedEndDate == null;
  }

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
    long avg = (salaryMin + salaryMax) / 2;
    if (avg > 0 && avg < 1_000_000) {
      avg *= 10_000L;
    }
    return avg;
  }

  /** 중복 제거 키. 묶음이 없으면 자기 자신이 하나의 묶음이다. */
  public UUID dedupKey() {
    return dedupGroupId != null ? dedupGroupId : id;
  }
}
