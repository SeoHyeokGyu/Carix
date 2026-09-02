package com.carix.snapshot.domain;

import com.carix.snapshot.DecisionSignalCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 월별 지수 스냅샷. 같은 {@code (user_id, base_year_month, data_version, rule_version)} 재실행은 기존 행을 대체하고 이전
 * 버전 행은 근거 추적을 위해 남긴다 (기술 명세 §1.3).
 *
 * <p>지수 컬럼은 계산 불가면 {@code null} 이다. 0 이나 100 으로 채우지 않는다.
 */
@Entity
@Table(
    name = "index_snapshot",
    schema = "core",
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {"user_id", "base_year_month", "data_version", "rule_version"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class IndexSnapshot {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "base_year_month", nullable = false, length = 6)
  private String baseYearMonth;

  @Column(name = "data_version", nullable = false, length = 50)
  private String dataVersion;

  @Column(name = "rule_version", nullable = false, length = 50)
  private String ruleVersion;

  @Column(name = "purchasing_power_index", precision = 6, scale = 2)
  private BigDecimal purchasingPowerIndex;

  @Column(name = "market_value_index", precision = 6, scale = 2)
  private BigDecimal marketValueIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision_signal", nullable = false, length = 50)
  private DecisionSignalCode decisionSignal;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "rationale", nullable = false)
  private SnapshotRationale rationale;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  public IndexSnapshot(
      UUID userId,
      String baseYearMonth,
      String dataVersion,
      String ruleVersion,
      BigDecimal purchasingPowerIndex,
      BigDecimal marketValueIndex,
      DecisionSignalCode decisionSignal,
      SnapshotRationale rationale) {
    this.userId = userId;
    this.baseYearMonth = baseYearMonth;
    this.dataVersion = dataVersion;
    this.ruleVersion = ruleVersion;
    this.purchasingPowerIndex = purchasingPowerIndex;
    this.marketValueIndex = marketValueIndex;
    this.decisionSignal = decisionSignal;
    this.rationale = rationale;
  }

  /** 같은 키의 재실행이 기존 행을 대체한다. 키 네 컬럼은 바뀌지 않는다. */
  public void replaceWith(
      BigDecimal purchasingPowerIndex,
      BigDecimal marketValueIndex,
      DecisionSignalCode decisionSignal,
      SnapshotRationale rationale) {
    this.purchasingPowerIndex = purchasingPowerIndex;
    this.marketValueIndex = marketValueIndex;
    this.decisionSignal = decisionSignal;
    this.rationale = rationale;
  }
}
