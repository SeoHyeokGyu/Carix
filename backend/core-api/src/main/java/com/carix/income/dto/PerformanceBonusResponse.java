package com.carix.income.dto;

import com.carix.income.domain.PerformanceBonus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "성과급 수령 기록")
public record PerformanceBonusResponse(
    UUID id,
    @Schema(description = "지급월 (yyyyMM)", example = "202607") String paidMonth,
    @Schema(description = "지급 금액 (원)", example = "3000000") long amount) {

  public static PerformanceBonusResponse from(PerformanceBonus bonus) {
    return new PerformanceBonusResponse(bonus.getId(), bonus.getPaidMonth(), bonus.getAmount());
  }
}
