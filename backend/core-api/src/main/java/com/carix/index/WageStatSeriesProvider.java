package com.carix.index;

import com.carix.index.dto.WageStatSeries;

/**
 * 공식 임금통계 연도 시계열 제공자 (Member B 소유, US-D8, 기능 명세 §4.9).
 *
 * <p>주어진 직군과 경력 브래킷에 대해 KOSIS 및 공식 임금통계의 연도별 관측치와 직전 연도 대비 구간 상승률을 제공합니다. Member C 의 대시보드 상승률 비교
 * 서비스 및 S-14 차트에서 이를 주입받아 소비합니다.
 */
public interface WageStatSeriesProvider {

  /**
   * 직군과 경력 브래킷에 대한 공식 임금통계 연도 시계열을 조회한다.
   *
   * @param jobFamily 직군 카테고리 (예: DEVELOPER)
   * @param experienceBracket 6구간 경력 브래킷 (예: MID)
   * @return 임금통계 연도 시계열 결과
   */
  WageStatSeries findSeries(String jobFamily, String experienceBracket);
}
