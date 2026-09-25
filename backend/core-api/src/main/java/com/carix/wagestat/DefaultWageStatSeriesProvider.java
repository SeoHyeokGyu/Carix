package com.carix.wagestat;

import com.carix.index.WageStatSeriesProvider;
import com.carix.index.dto.WageStatSeries;
import com.carix.index.dto.WageStatSeriesPoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 공식 임금통계 연도 시계열 기본 제공자 (Member B 구현체, US-D8, 기능 명세 §4.9).
 *
 * <p>{@code core.wage_stat_observation} 테이블을 조회하여 주어진 직군 및 경력 브래킷의 연도별 중앙값과 KOSIS 실측 기반 전년 대비 구간
 * 상승률을 산출합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultWageStatSeriesProvider implements WageStatSeriesProvider {

  private final WageStatObservationRepository wageStatObservationRepository;

  private static final String REGION_ALL = "ALL";
  private static final String DEFAULT_WAGE_DEFINITION = "ANNUAL_GROSS_MEDIAN";

  @Override
  public WageStatSeries findSeries(String jobFamily, String experienceBracket) {
    if (jobFamily == null
        || jobFamily.isBlank()
        || experienceBracket == null
        || experienceBracket.isBlank()) {
      return new WageStatSeries(jobFamily, experienceBracket, REGION_ALL, List.of());
    }

    String jf = jobFamily.trim();
    String eb = experienceBracket.trim();

    // 1. 전국/ALL 기준 공식 임금통계 조회 (임금통계 수집 시 region="ALL" 고정)
    List<WageStatObservation> obsList =
        wageStatObservationRepository.findSeriesObservations(
            jf, eb, REGION_ALL, DEFAULT_WAGE_DEFINITION);

    String resolvedRegion = REGION_ALL;

    // ALL 에 없으면 전국으로 fallback 조회
    if (obsList.isEmpty()) {
      obsList =
          wageStatObservationRepository.findSeriesObservations(
              jf, eb, "전국", DEFAULT_WAGE_DEFINITION);
      if (!obsList.isEmpty()) {
        resolvedRegion = "전국";
      }
    }

    // wageDefinition 지정 조회로 없을 경우 전체 definition fallback
    if (obsList.isEmpty()) {
      obsList = wageStatObservationRepository.findSeriesObservations(jf, eb, REGION_ALL, null);
      if (obsList.isEmpty()) {
        obsList = wageStatObservationRepository.findSeriesObservations(jf, eb, "전국", null);
        if (!obsList.isEmpty()) {
          resolvedRegion = "전국";
        }
      }
    }

    if (obsList.isEmpty()) {
      return new WageStatSeries(jf, eb, resolvedRegion, List.of());
    }

    // 2. basePeriod(연도)별로 최적 관측치 1건씩 선택
    // 쿼리 정렬이 `basePeriod ASC, source 우선순위(KOSIS > UNKNOWN > FALLBACK), statVintage DESC` 순이므로
    // basePeriod 별 첫 번째 행이 최우선 관측치다.
    Map<String, WageStatObservation> byYear = new LinkedHashMap<>();
    for (WageStatObservation obs : obsList) {
      if (obs.getBasePeriod() != null && !obs.getBasePeriod().isBlank()) {
        byYear.putIfAbsent(obs.getBasePeriod().trim(), obs);
      }
    }

    List<WageStatObservation> distinctYears = new ArrayList<>(byYear.values());
    distinctYears.sort(Comparator.comparing(WageStatObservation::getBasePeriod));

    // 3. 연도별 포인트 및 전년 대비 구간 상승률 산출
    // 기능 명세 §4.9 규칙:
    // - 구간 상승률 = (해당 연도 중앙값 − 직전 연도 중앙값) ÷ 직전 연도 중앙값 * 100
    // - source = KOSIS 인 두 점 사이에서만 낸다 (FALLBACK, UNKNOWN 은 null 처리).
    // - 연속된 두 해 사이에서만 계산 (연도 차이 == 1). 끊기면 null.
    // - 소수점 1자리 반올림 (예: 4.5%).
    List<WageStatSeriesPoint> points = new ArrayList<>();
    WageStatObservation prev = null;

    for (WageStatObservation curr : distinctYears) {
      Double growthRate = null;
      if (prev != null) {
        boolean isConsecutive = isConsecutiveYear(prev.getBasePeriod(), curr.getBasePeriod());
        boolean isBothKosis =
            prev.getSource() == WageStatSource.KOSIS && curr.getSource() == WageStatSource.KOSIS;

        if (isConsecutive
            && isBothKosis
            && prev.getWageMedian() != null
            && prev.getWageMedian() > 0
            && curr.getWageMedian() != null) {
          double rate =
              ((double) (curr.getWageMedian() - prev.getWageMedian()) / prev.getWageMedian())
                  * 100.0;
          growthRate = BigDecimal.valueOf(rate).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
      }

      points.add(
          new WageStatSeriesPoint(
              curr.getBasePeriod(),
              curr.getWageMedian() != null ? curr.getWageMedian() : 0L,
              curr.getWageDefinition(),
              curr.getSource() != null ? curr.getSource() : WageStatSource.UNKNOWN,
              curr.getStatVintage(),
              growthRate));

      prev = curr;
    }

    return new WageStatSeries(jf, eb, resolvedRegion, points);
  }

  private boolean isConsecutiveYear(String prevYear, String currYear) {
    try {
      int p = Integer.parseInt(prevYear);
      int c = Integer.parseInt(currYear);
      return c - p == 1;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
