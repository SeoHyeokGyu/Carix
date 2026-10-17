package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;

/**
 * 동료집단 연봉 분포 응답 (API 규격 §5.2).
 *
 * <p>표본이 모자라도 {@code 200 OK} 다 — 규격이 204 를 금지한다. 그때는 {@code status} 가 {@code INSUFFICIENT_SAMPLE}
 * 이고 {@code postingReference}·{@code userPosition} 이 {@code null} 이다. 화면은 `표본 부족` 배지와 표본 수를 함께
 * 보여준다.
 */
@Schema(description = "동료집단 연봉 분포 응답")
public record SalaryDistributionResponse(
    @Schema(
            description = "산출 상태",
            allowableValues = {"OK", "INSUFFICIENT_SAMPLE"})
        String status,
    @Schema(description = "중복 제거 후 동료집단 공고 수") Integer sampleSize,
    @Schema(description = "최소 표본 기준. 표본 부족일 때만 채운다", nullable = true) Integer minRequired,
    @Schema(nullable = true) WageBaseline baseline,
    @Schema(description = "표본 부족 시 null", nullable = true) PostingReference postingReference,
    @Schema(description = "연봉 이력이 없으면 null", nullable = true) UserPosition userPosition,
    @Schema(nullable = true) DistributionPeerGroup peerGroup,
    @Schema(description = "등록월별 제시 연봉 추이 (기능 명세 §4.6)") List<SalaryTrendPoint> trend,
    @Schema(description = "직군별 경력 구간 임금 사다리 (기능 명세 §4.7)", nullable = true)
        CareerLadder careerLadder,
    @Schema(description = "무작위 표본 기준 최근·이전 3개월 비교. 표본 월이 없으면 null", nullable = true)
        TrendComparison trendComparison) {

  public SalaryDistributionResponse(
      String status,
      Integer sampleSize,
      Integer minRequired,
      WageBaseline baseline,
      PostingReference postingReference,
      UserPosition userPosition,
      DistributionPeerGroup peerGroup,
      List<SalaryTrendPoint> trend,
      CareerLadder careerLadder) {
    this(
        status,
        sampleSize,
        minRequired,
        baseline,
        postingReference,
        userPosition,
        peerGroup,
        trend,
        careerLadder,
        null);
  }

  public SalaryDistributionResponse(
      String status,
      Integer sampleSize,
      Integer minRequired,
      WageBaseline baseline,
      PostingReference postingReference,
      UserPosition userPosition,
      DistributionPeerGroup peerGroup) {
    this(
        status,
        sampleSize,
        minRequired,
        baseline,
        postingReference,
        userPosition,
        peerGroup,
        Collections.emptyList(),
        null,
        null);
  }

  public SalaryDistributionResponse(
      String status,
      Integer sampleSize,
      Integer minRequired,
      WageBaseline baseline,
      PostingReference postingReference,
      UserPosition userPosition,
      DistributionPeerGroup peerGroup,
      List<SalaryTrendPoint> trend) {
    this(
        status,
        sampleSize,
        minRequired,
        baseline,
        postingReference,
        userPosition,
        peerGroup,
        trend,
        null,
        null);
  }

  /** 최근·이전 3개월 제시 연봉 비교. 월은 {@code uuuu-MM}. */
  public record TrendComparison(
      TrendPeriod recent,
      TrendPeriod previous,
      @Schema(allowableValues = {"UP", "DOWN", "FLAT", "INSUFFICIENT_SAMPLE"}) String verdict,
      List<String> sources,
      List<String> inProgress) {}

  public record TrendPeriod(
      String from,
      String to,
      @Schema(description = "표본 부족 시 null", nullable = true) Long median,
      @Schema(nullable = true) Long ciLow,
      @Schema(nullable = true) Long ciHigh,
      int disclosedSampleSize) {}

  public record CareerLadder(
      String userBracket,
      String basePeriod,
      List<String> availableYears,
      List<CareerLadderStep> steps) {
    public CareerLadder(String userBracket, List<CareerLadderStep> steps) {
      this(userBracket, null, Collections.emptyList(), steps);
    }
  }

  public record CareerLadderStep(
      String bracket, Long median, Double raiseFromPrevious, String source) {}

  public record SalaryTrendPoint(
      String yearMonth,
      @Schema(description = "공개연봉 표본 부족(<10) 시 null", nullable = true) Long median,
      int sampleSize,
      int disclosedSampleSize,
      double missingRate) {}

  public record WageBaseline(
      String basis, String source, @Schema(nullable = true) Long median, String wageDefinition) {
    public WageBaseline(String source, Long median, String wageDefinition) {
      this("AGE_BAND_PROXY", source, median, wageDefinition);
    }
  }

  /**
   * 유효 공개연봉만으로 낸 5수치.
   *
   * <p><b>여기의 {@code sampleSize} 는 최상위 {@code sampleSize} 와 다르다.</b> 최상위는 동료집단 공고 수이고 이쪽은 그중 연봉이
   * 공개된 건수다. {@code minRequired} 는 최상위에만 걸리므로 동료집단이 10건이어도 공개연봉이 1건이면 5수치가 한 점으로 뭉친 채 {@code OK} 로
   * 나간다 — 규격이 공고 분포를 참고 표시 전용으로 두기 때문이다. 화면은 {@code missingRate} 와 이 {@code sampleSize} 로 신뢰도를
   * 판단한다.
   */
  public record PostingReference(
      @Schema(nullable = true) Long min,
      @Schema(nullable = true) Long p25,
      @Schema(nullable = true) Long median,
      @Schema(nullable = true) Long p75,
      @Schema(nullable = true) Long max,
      int sampleSize,
      @Schema(description = "동료집단 공고 중 연봉 미공개 비율(%)") double missingRate) {}

  public record UserPosition(Long grossSalary, Integer percentile) {}

  public record DistributionPeerGroup(
      @Schema(description = "안착한 완화 단계 (1~5). 완화에 실패하면 null", nullable = true)
          Integer fallbackLevel,
      String appliedConditions) {}
}
