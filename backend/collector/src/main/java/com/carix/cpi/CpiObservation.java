package com.carix.cpi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 통계청(KOSIS) 지출목적별 소비자물가지수 관측치 엔티티.
 *
 * <p>구매력 지수(PPI) 계산의 기준 원천이며, 카테고리는 KOSIS 12대 분류 코드(A~L)와 총지수 '0' 단위로 원값을 보존한다. 총지수는 개인물가와 견주는 "전체
 * CPI" 다. Carix 6개 소비 카테고리로의 매핑·합성은 {@code cpi-category-map.yml} / {@code
 * default-expense-weights.yml} 기준으로 Index Engine이 계산 시점에 수행하며, 이 테이블에는 합성값을 저장하지 않는다.
 */
@Entity
@Table(
    name = "cpi_observation",
    schema = "core",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_cpi_observation_dimension",
          columnNames = {"base_year_month", "category"})
    })
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CpiObservation {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  @Column(name = "base_year_month", nullable = false, length = 6)
  private String baseYearMonth;

  /** KOSIS 지출목적별 12대 분류 코드 (A~L) 또는 총지수 '0'. 카테고리 지수는 설정된 코드로만 읽으므로 '0' 행은 합성에 섞이지 않는다. */
  @Column(nullable = false, length = 1)
  private String category;

  @Column(name = "cpi_value", nullable = false, precision = 10, scale = 4)
  private BigDecimal cpiValue;
}
