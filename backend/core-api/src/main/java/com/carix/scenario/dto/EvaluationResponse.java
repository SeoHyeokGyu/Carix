package com.carix.scenario.dto;

import com.carix.scenario.dto.SimulationResponse.FeasibilityDetail;
import com.carix.scenario.inflation.InflationBasis;
import com.carix.scenario.projection.ProjectionResult.FirstYear;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "세후·실질 평가 결과. 결론 숫자와 계단은 확정층만이다")
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationResponse(
    StayView stay,
    @Schema(description = "이직 입력이 없으면 null", nullable = true) MoveView move,
    InflationBasis inflation,
    List<Assumption> assumptions,
    @Schema(description = "넣지 않아 계산에 반영되지 않은 항목 코드") List<String> unreflected,
    @Schema(description = "근속 1년 직전일 때만", nullable = true) SeveranceWarning severance,
    @Schema(description = "옮기면 추정 이득 / 남으면 추정 이득. 이직이 있을 때만", nullable = true) String typeLabel) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StayView(
      @Schema(description = "내 기준 물가로 본 실질 소득 변화(%)") double realChangePercent,
      @Schema(description = "한국은행 전망 물가로 본 실질 소득 변화(%)") double realChangeGovernmentPercent,
      @Schema(description = "내 기준 물가에서 실질 변화가 0 이 되는 인상률(%). 개인물가를 못 구하면 null", nullable = true)
          Double breakevenPersonalPercent,
      @Schema(description = "한국은행 전망 물가에서 실질 변화가 0 이 되는 인상률(%)", nullable = true)
          Double breakevenGovernmentPercent,
      @Schema(description = "임금통계 중앙값 대비 격차(%, 세전). 산출 불가면 null", nullable = true)
          Double marketGapPercent) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record MoveView(
      long[] stayCumulative,
      long[] moveCumulative,
      @Schema(nullable = true) Integer crossoverMonth,
      FirstYear firstYear,
      @Schema(description = "기대층(성과급·사이닝) 첫해 차이 범위. 결론에 더하지 않는다") Range expectedRange,
      @Schema(description = "불확실층(주식). 입력이 없으면 null", nullable = true) StockLine stock,
      @Schema(nullable = true) Double equalizingRaisePercent,
      FeasibilityDetail feasibility) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Range(long min, long max) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StockLine(Long stayVesting12m, Long forfeitedUnvested, Long moveVesting12m) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Assumption(String code, String text) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SeveranceWarning(int monthsToAnniversary, long estimatedAmount) {}
}
