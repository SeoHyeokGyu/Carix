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

/**
 * 사전 집계된 시장 채용공고 데이터 엔티티 (core.market_aggregate).
 *
 * <p>원천별(JOBKOREA, SARAMIN, WANTED, JUMPIT 등) 집계 데이터를 저장하며, 동료집단 완화 단계(Level 1~5)에 따라 회사 규모나 고용 형태가
 * NULL(전체 롤업)인 행을 포함합니다.
 *
 * <p>스킬 ID가 NULL인 행은 해당 동료집단 조건의 '전체 공고 기준 행'이며, 스킬 ID가 존재하는 행은 해당 조건 내 특정 스킬의 출현 빈도 및 수요율을 나타냅니다.
 */
@Entity
@Table(schema = "core", name = "market_aggregate")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MarketAggregate {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  /** 채용공고 원천 플랫폼 코드 (예: JOBKOREA, SARAMIN, WANTED, JUMPIT) */
  @Column(nullable = false, length = 50)
  private String source;

  /** 집계 기준 년월 (YYYYMM, 6자리) */
  @Column(name = "base_year_month", nullable = false, length = 6)
  private String baseYearMonth;

  /** 직무 카테고리 (예: DEVELOPER, DATA, AI 등) */
  @Column(name = "job_family", nullable = false, length = 100)
  private String jobFamily;

  /** 지역 (예: 서울 강남구 등 상세 구 또는 서울특별시 등 시/도) */
  @Column(nullable = false, length = 100)
  private String region;

  /** 경력 구간 브래킷 (JUNIOR: 0~3년, MID: 4~7년, SENIOR: 8년+) */
  @Column(name = "experience_bracket", nullable = false, length = 50)
  private String experienceBracket;

  /** 고용 형태 (예: FULL_TIME, CONTRACT 등). NULL인 경우 고용 형태 무관 전체 롤업 행 (완화 Level 5). */
  @Column(name = "employment_type", length = 50)
  private String employmentType;

  /** 회사 규모 (예: LARGE, MEDIUM, SMALL 등). NULL인 경우 회사 규모 무관 전체 롤업 행 (완화 Level 2). */
  @Column(name = "company_size", length = 50)
  private String companySize;

  /** 연계된 스킬 ID. NULL인 경우 스킬 무관 동료집단 전체 행(총 공고수 집계 및 연봉 분포 정보 포함). */
  @Column(name = "skill_id")
  private UUID skillId;

  /** 해당 조건/스킬에 해당하는 공고 건수 */
  @Column(name = "posting_count", nullable = false)
  private Integer postingCount;

  /** 해당 동료집단 전체 공고 건수 (분모) */
  @Column(name = "total_aggregate_postings", nullable = false)
  private Integer totalAggregatePostings;

  /** 스킬 수요율 (posting_count / total_aggregate_postings). 단위: 비율 (0.0000 ~ 1.0000) */
  @Column(name = "demand_rate", nullable = false, precision = 5, scale = 4)
  private BigDecimal demandRate;

  /** 공개연봉 결측률 (단위: %, 0.00 ~ 100.00). 전체 공고 중 연봉이 명시되지 않은 공고의 백분율. */
  @Column(name = "salary_missing_rate", nullable = false, precision = 5, scale = 2)
  private BigDecimal salaryMissingRatePercent;
}
