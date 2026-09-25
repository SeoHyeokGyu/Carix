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

  @Column(nullable = false, length = 50)
  private String source;

  @Column(name = "base_year_month", nullable = false, length = 6)
  private String baseYearMonth;

  @Column(name = "job_family", nullable = false, length = 100)
  private String jobFamily;

  @Column(nullable = false, length = 100)
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

  /** 스킬 수요율. 단위는 비율이다 (0.0000 ~ 1.0000) — 퍼센트가 아니다. */
  @Column(name = "demand_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal demandRate;

  /** 연봉 결측률. 단위는 퍼센트다 (0.00 ~ 100.00) — 비율이 아니다. 같은 표의 demandRate 는 비율이므로 혼동하지 않는다. */
  @Column(name = "salary_missing_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal salaryMissingRatePercent;
}
