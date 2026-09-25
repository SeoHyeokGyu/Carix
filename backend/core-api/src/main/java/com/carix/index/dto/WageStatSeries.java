package com.carix.index.dto;

import java.util.List;

/**
 * 공식 임금통계 연도 시계열 (US-D8, 기능 명세 §4.9).
 *
 * @param jobFamily 직군 카테고리
 * @param experienceBracket 경력 구간 브래킷
 * @param region 지역 (기본: ALL)
 * @param points 연도 오름차순 임금통계 포인트 목록
 */
public record WageStatSeries(
    String jobFamily, String experienceBracket, String region, List<WageStatSeriesPoint> points) {

  public WageStatSeries {
    points = points == null ? List.of() : List.copyOf(points);
  }

  /**
   * 시계열 내에 KOSIS 연속 실측 기반의 유효한 구간 상승률이 1개 이상 존재하는지 여부.
   *
   * <p>모든 구간이 제외되면 빈 상태(EMPTY) 판정에 활용된다 (§4.9).
   */
  public boolean hasValidKosisGrowthInterval() {
    return points.stream().anyMatch(p -> p.growthRatePercent() != null);
  }
}
