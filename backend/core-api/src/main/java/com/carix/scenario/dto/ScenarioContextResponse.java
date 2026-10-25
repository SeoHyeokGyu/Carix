package com.carix.scenario.dto;

import com.carix.scenario.inflation.InflationBasis;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "시뮬레이터가 입력 기본값으로 쓰는 현재 상태")
public record ScenarioContextResponse(
    @Schema(description = "세전 현재 연 소득(따로 받는 고정상여 포함). 소득 이력이 없으면 null", nullable = true)
        Long currentAnnualGross,
    @Schema(description = "월 실수령액. 없으면 null", nullable = true) Long monthlyNet,
    @Schema(description = "입사일. 없으면 null", nullable = true) LocalDate hireDate,
    @Schema(description = "연봉에 고정상여가 포함돼 있는지") boolean bonusIncluded,
    @Schema(description = "따로 받는 고정상여 연액", nullable = true) Long fixedBonusAnnual,
    @Schema(description = "직전 인상 이력에서 읽은 기본 인상률(%). 알 수 없으면 null", nullable = true)
        Double defaultRaisePercent,
    @Schema(description = "최근 3개 연도 성과급 범위. 대상 연도가 없으면 null", nullable = true)
        BonusRange performanceBonusRange,
    @Schema(description = "한국은행 전망과 개인물가 두 기준") InflationBasis inflation,
    @Schema(
            description = "공식 임금통계 중앙값 대비 격차(%, 세전·명목). 양수면 중앙값이 더 높다. 산출 불가면 null",
            nullable = true)
        Double marketGapPercent) {

  /** 연도별 성과급 합의 최소·평균. {@code years} 는 대상 연도 수다. */
  public record BonusRange(long min, long average, int years) {}
}
