package com.carix.peergroup;

import java.time.YearMonth;
import java.util.List;

/**
 * 최근 3개월과 이전 3개월의 제시 연봉 중앙값 비교 결과.
 *
 * @param sources 두 기간 6개월을 모두 채운 공통 원천(사전순)
 * @param inProgress 마지막 표본 월 이후 현재 월까지 아직 표본이 없는 달
 */
public record TrendComparison(
    Period recent,
    Period previous,
    Verdict verdict,
    List<String> sources,
    List<YearMonth> inProgress) {

  public enum Verdict {
    UP,
    DOWN,
    FLAT,
    INSUFFICIENT_SAMPLE
  }

  /** 표본이 최소치에 못 미치면 median·ciLow·ciHigh 는 null. */
  public record Period(
      YearMonth from,
      YearMonth to,
      Long median,
      Long ciLow,
      Long ciHigh,
      int disclosedSampleSize) {}
}
