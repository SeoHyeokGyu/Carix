package com.carix.index.cpi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * {@code core.cpi_observation} 읽기 전용 뷰. 쓰기는 collector 소관이다 — core-api 는 이 테이블에 쓰지 않는다.
 *
 * <p>{@code category} 는 KOSIS 지출목적별 12대 분류 코드(A~L) 원값이다. Carix 카테고리 합성은 {@link
 * CategoryCpiRateService} 가 한다.
 */
@Entity
@Immutable
@Table(name = "cpi_observation", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CpiObservationRecord {

  @Id private UUID id;

  @Column(name = "base_year_month", nullable = false, length = 6)
  private String baseYearMonth;

  @Column(nullable = false, length = 50)
  private String category;

  @Column(name = "cpi_value", nullable = false, precision = 5, scale = 2)
  private BigDecimal cpiValue;

  /** 테스트 적재용. 운영 코드는 이 테이블에 쓰지 않는다. */
  static CpiObservationRecord of(String baseYearMonth, String category, String cpiValue) {
    CpiObservationRecord record = new CpiObservationRecord();
    record.id = UUID.randomUUID();
    record.baseYearMonth = baseYearMonth;
    record.category = category;
    record.cpiValue = new BigDecimal(cpiValue);
    return record;
  }
}
