package com.carix.income.domain;

import com.carix.common.EncryptedConverter;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 성과급 지급 기록. 같은 달에 여러 건이 있을 수 있다. */
@Entity
@Table(name = "performance_bonus", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PerformanceBonus {

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  /** 지급월 {@code yyyyMM}. */
  @Column(name = "paid_month", nullable = false, length = 6)
  private String paidMonth;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "amount_encrypted", nullable = false, length = 512)
  private Long amount;

  @Column(name = "created_at", insertable = false, updatable = false)
  private OffsetDateTime createdAt;

  public PerformanceBonus(UUID userId, String paidMonth, Long amount) {
    this.userId = userId;
    this.paidMonth = paidMonth;
    this.amount = amount;
  }
}
