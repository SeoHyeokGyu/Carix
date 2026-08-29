package com.carix.income.domain;

import com.carix.common.EncryptedConverter;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "income_history", schema = "core")
public class IncomeHistory {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "gross_salary_encrypted", nullable = false, length = 512)
  private Long grossSalary;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "net_salary_encrypted", length = 512)
  private Long netSalary;

  @Column(name = "effective_date", nullable = false)
  private LocalDate effectiveDate;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected IncomeHistory() {}

  public IncomeHistory(UUID userId, Long grossSalary, Long netSalary, LocalDate effectiveDate) {
    this.userId = userId;
    this.grossSalary = grossSalary;
    this.netSalary = netSalary;
    this.effectiveDate = effectiveDate;
  }

  public void update(Long grossSalary, Long netSalary, LocalDate effectiveDate) {
    this.grossSalary = grossSalary;
    this.netSalary = netSalary;
    this.effectiveDate = effectiveDate;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Long getGrossSalary() {
    return grossSalary;
  }

  public Long getNetSalary() {
    return netSalary;
  }

  public LocalDate getEffectiveDate() {
    return effectiveDate;
  }
}
