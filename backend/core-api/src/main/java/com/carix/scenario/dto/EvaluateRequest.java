package com.carix.scenario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Schema(description = "세후·실질 평가 요청. 저장하지 않는다")
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluateRequest(
    @NotNull @Valid Stay stay,
    @Schema(description = "이직 입력. 없으면 잔류만 평가한다", nullable = true) @Valid Move move) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Stay(
      @NotNull
          @DecimalMin("-100")
          @DecimalMax("100")
          @Schema(description = "잔류 시 연 인상률 (%)", example = "3.5")
          Double raisePercent,
      @PositiveOrZero @Schema(description = "현재 세전 연봉을 직접 지정할 때(가상 입력). 없으면 소득 이력", nullable = true)
          Long currentGrossOverride,
      @PositiveOrZero @Schema(description = "잔류 성과급 기대액 (원)", nullable = true)
          Long performanceBonusEstimate,
      @PositiveOrZero @Schema(description = "잔류 시 12개월 안에 베스팅되는 주식 평가액 (원)", nullable = true)
          Long stockVesting12m,
      @PositiveOrZero @Schema(description = "이직하면 포기하는 미베스팅 주식 평가액 (원)", nullable = true)
          Long stockUnvested) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Move(
      @NotNull @PositiveOrZero @Schema(description = "이직 세전 연봉 (원)", example = "72000000")
          Long grossSalary,
      @NotNull @Min(0) @Max(6) @Schema(description = "이직 공백 개월수 (0~6)", example = "2")
          Integer unemploymentMonths,
      @NotNull @PositiveOrZero @Schema(description = "이직 일회성 비용 (원)", example = "3000000")
          Long oneTimeCost,
      @NotNull @Schema(description = "실업급여 수급 자격") Boolean benefitEligible,
      @NotNull @Schema(description = "이직 연봉에 고정상여가 포함돼 있는지") Boolean bonusIncluded,
      @PositiveOrZero @Schema(description = "따로 받는 고정상여 연액 (원)", nullable = true)
          Long fixedBonusAnnual,
      @PositiveOrZero @Schema(description = "이직처 성과급 기대액 (원)", nullable = true)
          Long performanceBonusExpected,
      @PositiveOrZero @Schema(description = "사이닝 보너스 (원)", nullable = true) Long signingBonus,
      @PositiveOrZero @Schema(description = "이직처 12개월 베스팅 주식 평가액 (원)", nullable = true)
          Long stockVesting12m) {}
}
