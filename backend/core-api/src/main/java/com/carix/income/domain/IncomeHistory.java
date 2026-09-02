package com.carix.income.domain;

import com.carix.common.EncryptedConverter;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "income_history", schema = "core")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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
}
