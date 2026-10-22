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
import org.hibernate.annotations.ColumnDefault;

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

  /** 연봉 계약 금액에 상여가 들어 있는가. {@code false} 면 상여는 {@link #fixedBonusAnnual} 로 따로 받는다. */
  @Builder.Default
  @ColumnDefault("true")
  @Column(name = "bonus_included", nullable = false)
  private boolean bonusIncluded = true;

  /** 계약 금액 밖에서 따로 받는 고정 상여의 연 금액. 상여가 포함돼 있거나 모르면 {@code null}. */
  @Convert(converter = EncryptedConverter.class)
  @Column(name = "fixed_bonus_annual_encrypted", length = 512)
  private Long fixedBonusAnnual;

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
    this.bonusIncluded = true;
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

  public void applyBonusTerms(boolean bonusIncluded, Long fixedBonusAnnual) {
    this.bonusIncluded = bonusIncluded;
    this.fixedBonusAnnual = fixedBonusAnnual;
  }
}
