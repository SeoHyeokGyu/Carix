package com.carix.wagestat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연봉 격차 판정의 기준선인 공식 임금통계 관측치 엔티티.
 *
 * <p>KOSIS 고용형태별근로실태조사(DT_118N_PAYM39)의 24개 임금계층 누적분포 보간을 통해 산출된 연간 세전 중위임금(wage_median)을 보존합니다.
 */
@Entity
@Table(
    name = "wage_stat_observation",
    schema = "core",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_wage_stat_dimension",
          columnNames = {
            "job_family",
            "experience_bracket",
            "region",
            "base_period",
            "stat_vintage"
          })
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WageStatObservation {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  @Column(name = "job_family", nullable = false, length = 100)
  private String jobFamily;

  @Column(name = "experience_bracket", nullable = false, length = 50)
  private String experienceBracket;

  @Column(name = "region", nullable = false, length = 100)
  private String region;

  @Column(name = "wage_definition", nullable = false, length = 100)
  private String wageDefinition;

  @Column(name = "wage_median", nullable = false)
  private Long wageMedian;

  @Column(name = "base_period", nullable = false, length = 6)
  private String basePeriod;

  @Column(name = "stat_vintage", nullable = false, length = 6)
  private String statVintage;
}
