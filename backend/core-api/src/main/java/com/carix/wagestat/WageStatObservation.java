package com.carix.wagestat;

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
 * 공식 임금통계 기준선 데이터 엔티티 (core.wage_stat_observation).
 *
 * <p>KOSIS 임금통계를 기반으로 24구간 보간 적재된 데이터로, 연봉 격차 판정의 기준선(연간 세전 중앙값)을 제공합니다.
 */
@Entity
@Table(schema = "core", name = "wage_stat_observation")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WageStatObservation {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  /** 직무 카테고리 (예: DEVELOPER, DATA 등) */
  @Column(name = "job_family", nullable = false, length = 100)
  private String jobFamily;

  /** 경력 구간 브래킷 (JUNIOR, MID, SENIOR) */
  @Column(name = "experience_bracket", nullable = false, length = 50)
  private String experienceBracket;

  /** 지역 (예: 서울특별시, 전국 등) */
  @Column(nullable = false, length = 100)
  private String region;

  /** 임금 정의 문구 (예: ANNUAL_GROSS_MEDIAN, 초과급여 제외, 상여 포함 등) */
  @Column(name = "wage_definition", nullable = false, length = 100)
  private String wageDefinition;

  /** 연간 세전 중앙값 (원 단위) */
  @Column(name = "wage_median", nullable = false)
  private Long wageMedian;

  /** 통계 기준 기간 (YYYYMM) */
  @Column(name = "base_period", nullable = false, length = 6)
  private String basePeriod;

  /** 통계 개정판 (YYYYMM) */
  @Column(name = "stat_vintage", nullable = false, length = 6)
  private String statVintage;
}
