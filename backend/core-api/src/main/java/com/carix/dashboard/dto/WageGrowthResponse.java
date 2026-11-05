package com.carix.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 내 연봉과 같은 경력 구간 임금의 누적 상승 비교 (기능 명세 US-D8·§4.9). 본인에게만 나가는 응답이고 저장하지 않는다.
 *
 * @param bracket 시장 계열의 경력 구간. 지금 내 구간에 고정한다
 * @param basis 기준선 성격. 연령대를 경력 구간으로 옮긴 값이고 전 직무 공통이다
 * @param source 기준선 출처. 상승률은 KOSIS 실측에서만 낸다
 * @param latestStatYear 쓸 수 있는 KOSIS 의 마지막 연도. 없으면 null
 * @param lastRecordedYear 사용자의 마지막 기록 연도. 없으면 null
 * @param latest 가장 최근 실행의 누적. {@code OK} 가 아니면 null
 * @param runs KOSIS 실측이 이어지는 묶음. {@code OK} 가 아니면 빈 목록
 */
@Schema(description = "상승률 비교 응답")
public record WageGrowthResponse(
    Status status,
    String bracket,
    String basis,
    String source,
    @Schema(nullable = true) String latestStatYear,
    @Schema(nullable = true) String lastRecordedYear,
    @Schema(nullable = true) Latest latest,
    List<Run> runs) {

  public enum Status {
    OK,
    NO_INCOME_HISTORY,
    NO_WAGE_STAT,
    NO_OVERLAP
  }

  /**
   * 한 실행의 양 끝 누적.
   *
   * @param gapPp 표시되는 내 누적 − 시장 누적 (%p)
   */
  public record Latest(
      String fromYear,
      String toYear,
      double myGrowthPercent,
      double marketGrowthPercent,
      double gapPp) {}

  public record Run(List<Point> points, List<Span> spans) {}

  /**
   * 한 해의 점. 실행 시작 연도가 100 이다.
   *
   * @param recorded 그해에 소득 행이 있는가. 없으면 직전 지수를 유지한 값이다
   * @param myGross 그해 연 소득(원). 기록이 없는 해는 null — 이어 온 값을 그해 연봉처럼 보이면 안 된다
   */
  public record Point(
      String year,
      double myIndex,
      double marketIndex,
      boolean recorded,
      @Schema(nullable = true) Long myGross,
      long marketMedian) {

    /** 금액을 가린다 — 완료 기준 10 이 금액이 로그에 남는 것을 금지한다. */
    @Override
    public String toString() {
      return "Point[year=%s, recorded=%s, myGross=***]".formatted(year, recorded);
    }
  }

  /** 기록 연도 사이 한 구간. 사이에 빈 해가 있으면 시장도 같은 기간으로 합친다. */
  public record Span(
      String fromYear, String toYear, double myGrowthPercent, double marketGrowthPercent) {}
}
