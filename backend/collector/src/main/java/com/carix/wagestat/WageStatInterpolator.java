package com.carix.wagestat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** KOSIS 24개 임금 계층(PAYGRADE)의 근로자 수 분포를 바탕으로 누적분포 선형 보간을 수행하는 계산기. */
@Component
public class WageStatInterpolator {

  public record BracketDefinition(
      String code, double minMonthlyThousand, double maxMonthlyThousand) {}

  public record BracketCount(BracketDefinition bracket, long workerCount) {}

  public static final List<BracketDefinition> BRACKETS =
      List.of(
          new BracketDefinition("A01", 0.0, 800.0),
          new BracketDefinition("A02", 800.0, 900.0),
          new BracketDefinition("A03", 900.0, 1000.0),
          new BracketDefinition("A04", 1000.0, 1100.0),
          new BracketDefinition("A05", 1100.0, 1200.0),
          new BracketDefinition("A06", 1200.0, 1300.0),
          new BracketDefinition("A07", 1300.0, 1400.0),
          new BracketDefinition("A08", 1400.0, 1500.0),
          new BracketDefinition("A09", 1500.0, 1600.0),
          new BracketDefinition("A10", 1600.0, 1700.0),
          new BracketDefinition("A11", 1700.0, 1800.0),
          new BracketDefinition("A12", 1800.0, 1900.0),
          new BracketDefinition("A13", 1900.0, 2000.0),
          new BracketDefinition("A14", 2000.0, 2200.0),
          new BracketDefinition("A15", 2200.0, 2400.0),
          new BracketDefinition("A16", 2400.0, 2600.0),
          new BracketDefinition("A17", 2600.0, 2800.0),
          new BracketDefinition("A18", 2800.0, 3000.0),
          new BracketDefinition("A19", 3000.0, 3500.0),
          new BracketDefinition("A20", 3500.0, 4000.0),
          new BracketDefinition("A21", 4000.0, 4500.0),
          new BracketDefinition("A22", 4500.0, 5000.0),
          new BracketDefinition("A23", 5000.0, 6000.0),
          new BracketDefinition("A24", 6000.0, 8000.0));

  /**
   * 계층별 근로자 수 맵으로부터 세전 연봉 중앙값(원 단위)을 산출합니다.
   *
   * @param countsByCode KOSIS 임금계층 코드(A01~A24)별 근로자 수
   * @return 연간 세전 중위임금 (원 단위)
   */
  public long calculateAnnualMedianWage(Map<String, Long> countsByCode) {
    return calculateAnnualPercentileWage(countsByCode, 0.5);
  }

  /**
   * 계층별 근로자 수 맵으로부터 지정 분위수(percentile, 0.0~1.0)에 해당하는 세전 연봉을 보간 산출합니다.
   *
   * @param countsByCode KOSIS 임금계층 코드(A01~A24)별 근로자 수
   * @param percentile 분위수 (예: 0.5 = 중앙값, 0.25 = 하위 25%, 0.75 = 상위 75%)
   * @return 연간 세전 임금 (원 단위)
   */
  public long calculateAnnualPercentileWage(Map<String, Long> countsByCode, double percentile) {
    if (countsByCode == null || countsByCode.isEmpty()) {
      return 0L;
    }

    long totalWorkers = 0L;
    List<BracketCount> list = new ArrayList<>();
    for (BracketDefinition def : BRACKETS) {
      long count = countsByCode.getOrDefault(def.code(), 0L);
      list.add(new BracketCount(def, count));
      totalWorkers += count;
    }

    if (totalWorkers <= 0L) {
      return 0L;
    }

    double targetRank = totalWorkers * percentile;
    long cumulative = 0L;

    for (BracketCount bc : list) {
      long nextCumulative = cumulative + bc.workerCount();
      if (nextCumulative >= targetRank && bc.workerCount() > 0) {
        double ratio = (targetRank - cumulative) / (double) bc.workerCount();
        double span = bc.bracket().maxMonthlyThousand() - bc.bracket().minMonthlyThousand();
        double interpolatedMonthlyThousand = bc.bracket().minMonthlyThousand() + (span * ratio);

        // 월급(천원) -> 연봉(원 단위): * 12 * 1000
        return Math.round(interpolatedMonthlyThousand * 12.0 * 1000.0);
      }
      cumulative = nextCumulative;
    }

    // 최상위 구간 끝값 기준
    BracketDefinition last = BRACKETS.get(BRACKETS.size() - 1);
    return Math.round(last.maxMonthlyThousand() * 12.0 * 1000.0);
  }
}
