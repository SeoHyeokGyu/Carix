package com.carix.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "core", name = "market_aggregate")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketAggregate {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  @Column(nullable = false, length = 20)
  private String source;

  @Column(name = "base_year_month", nullable = false, length = 7)
  private String baseYearMonth;

  @Column(name = "job_family", nullable = false, length = 50)
  private String jobFamily;

  @Column(nullable = false, length = 50)
  private String region;

  @Column(name = "experience_bracket", nullable = false, length = 50)
  private String experienceBracket;

  @Column(name = "employment_type", length = 50)
  private String employmentType;

  @Column(name = "company_size", length = 50)
  private String companySize;

  @Column(name = "skill_id")
  private UUID skillId;

  @Column(name = "posting_count", nullable = false)
  private Integer postingCount;

  @Column(name = "total_aggregate_postings", nullable = false)
  private Integer totalAggregatePostings;

  @Column(name = "demand_rate", nullable = false, precision = 6, scale = 4)
  private BigDecimal demandRate;

  @Column(name = "salary_min")
  private Long salaryMin;

  @Column(name = "salary_p25")
  private Long salaryP25;

  @Column(name = "salary_median")
  private Long salaryMedian;

  @Column(name = "salary_p75")
  private Long salaryP75;

  @Column(name = "salary_max")
  private Long salaryMax;

  @Column(name = "salary_missing_rate", nullable = false, precision = 6, scale = 4)
  private BigDecimal salaryMissingRate;
}
