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

  /** 사용자가 고른 값만 담는다. {@code INITIAL} 은 여기 들어오지 않는다 — enum 주석 참조. */
  @Enumerated(EnumType.STRING)
  @Column(name = "change_reason", length = 20)
  private IncomeChangeReasonEnum changeReason;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  public IncomeHistory(
      UUID userId,
      Long grossSalary,
      Long netSalary,
      LocalDate effectiveDate,
      IncomeChangeReasonEnum changeReason) {
    this.userId = userId;
    this.grossSalary = grossSalary;
    this.netSalary = netSalary;
    this.effectiveDate = effectiveDate;
    this.changeReason = changeReason;
  }

  public void update(
      Long grossSalary,
      Long netSalary,
      LocalDate effectiveDate,
      IncomeChangeReasonEnum changeReason) {
    this.grossSalary = grossSalary;
    this.netSalary = netSalary;
    this.effectiveDate = effectiveDate;
    this.changeReason = changeReason;
  }
}
