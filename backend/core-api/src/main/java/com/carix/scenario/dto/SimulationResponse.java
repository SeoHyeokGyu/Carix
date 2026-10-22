package com.carix.scenario.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "시뮬레이션 결과 응답")
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationResponse(
    @Schema(description = "시나리오 고유 ID") UUID id,
    @Schema(description = "시나리오 이름. 이름 없이 저장했으면 null", nullable = true) String scenarioName,
    @Schema(description = "12개월 추정 잔류 보상액", example = "62100000") long stayCompensation12m,
    @Schema(description = "12개월 추정 이직 보상액", example = "60960000") long moveCompensation12m,
    @Schema(description = "실업급여 추정액", example = "3960000") long estimatedUnemploymentBenefit,
    @Schema(
            description = "실업급여 산출 안내. 수급 자격이 없으면 `실업급여 미반영`, 있으면 가정치의 기준 시점을 알린다",
            example = "가정 기반 추정 (기준 시점: 2026-01)")
        String unemploymentBenefitNote,
    @Schema(description = "잔류와 이직 간 차액", example = "-1140000") long difference,
    @Schema(
            description = "결과 유형 코드 (ESTIMATED_GAIN / ESTIMATED_OPPORTUNITY_COST)",
            example = "ESTIMATED_OPPORTUNITY_COST")
        String type,
    @Schema(description = "결과 유형 라벨명", example = "추정 기회비용") String typeLabel,
    @Schema(description = "주의 사항 텍스트", example = "이 계산에는 세금, 성과급...") String warningNote,
    @Schema(description = "목표 연봉의 시장 실현 가능성 분석 정보") FeasibilityDetail feasibility) {

  /**
   * 목표 연봉의 실현 가능성.
   *
   * <p>동료집단 공고 분포에서 나온다(API 규격 §6.1). 표본이 모자라면 객체 자체는 남고 안의 값이 {@code null} 이 되며 {@code status} 가
   * 사유를 말한다 — 지수와 같은 규약이다. 임의 분포로 백분위를 지어내지 않는다 ([MVP 규격 §13] 데이터가 없는 기능을 추정값으로 채워 출시하지 않는다).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FeasibilityDetail(
      @Schema(
              description = "산출 상태",
              allowableValues = {"OK", "INSUFFICIENT_SAMPLE"},
              example = "OK")
          String status,
      @Schema(description = "백분위를 매긴 공개연봉 표본 수", example = "42") Integer sampleSize,
      @Schema(description = "최소 표본 기준. 표본 부족일 때만 채운다", nullable = true, example = "10")
          Integer minRequired,
      @Schema(description = "시장 실현 가능성 점수 (0~100)", nullable = true, example = "75")
          Integer feasibilityScore,
      @Schema(description = "목표 연봉의 백분위수", nullable = true, example = "78")
          Integer targetPercentile,
      @Schema(
              description = "등급 (EASY, REALISTIC, CHALLENGING, VERY_HIGH_CHALLENGE)",
              nullable = true,
              example = "CHALLENGING")
          String difficultyGrade,
      @Schema(description = "시장 대조 분석 피드백 코멘트", nullable = true) String feedbackMessage) {}
}
