package com.carix.dashboard.service;

import com.carix.dashboard.dto.WageGrowthResponse;
import com.carix.dashboard.dto.WageGrowthResponse.Latest;
import com.carix.dashboard.dto.WageGrowthResponse.Point;
import com.carix.dashboard.dto.WageGrowthResponse.Run;
import com.carix.dashboard.dto.WageGrowthResponse.Span;
import com.carix.dashboard.dto.WageGrowthResponse.Status;
import com.carix.index.dto.RecordedYearIncome;
import com.carix.index.dto.WageStatSeries;
import com.carix.index.dto.WageStatSeriesPoint;
import com.carix.wagestat.WageStatSource;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 상승률 비교 계산 (기능 명세 US-D8·§4.9). I/O 없이 재료만 받는다.
 *
 * <p>실행(run)은 KOSIS 실측이 이어지는 연도 묶음을 그 안의 첫·마지막 기록 연도로 자른 것이다. 양 끝이 항상 기록 연도라 동결인지 미입력인지 모르는 값으로
 * 시작하거나 끝나지 않는다. 지수와 상승률은 반올림된 {@code growthRatePercent} 가 아니라 원값에서 낸다.
 */
final class WageGrowthCalculator {

  private static final String BASIS = "AGE_BAND_PROXY";
  private static final String SOURCE = WageStatSource.KOSIS.name();
  private static final String GROSS_DEFINITION = "ANNUAL_GROSS_MEDIAN";
  private static final int MIN_POINTS = 2;
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private WageGrowthCalculator() {}

  static WageGrowthResponse calculate(List<RecordedYearIncome> incomes, WageStatSeries series) {
    NavigableMap<Integer, Long> recorded = new TreeMap<>();
    incomes.forEach(income -> recorded.put(income.year(), income.annualGross()));
    NavigableMap<Integer, Long> market = usableMedians(series.points());

    List<List<Integer>> marketRuns = consecutiveRuns(market);
    Status status;
    List<Run> runs = List.of();
    if (recorded.size() < MIN_POINTS) {
      status = Status.NO_INCOME_HISTORY;
    } else if (marketRuns.isEmpty()) {
      status = Status.NO_WAGE_STAT;
    } else {
      runs =
          marketRuns.stream()
              .map(years -> buildRun(years, recorded, market))
              .filter(Objects::nonNull)
              .toList();
      status = runs.isEmpty() ? Status.NO_OVERLAP : Status.OK;
    }

    return new WageGrowthResponse(
        status,
        series.experienceBracket(),
        BASIS,
        SOURCE,
        market.isEmpty() ? null : String.valueOf(market.lastKey()),
        recorded.isEmpty() ? null : String.valueOf(recorded.lastKey()),
        runs.isEmpty() ? null : latest(runs.getLast()),
        runs);
  }

  /** 상승률에 쓸 수 있는 연도 — 실측이고 같은 임금 정의이며 값이 있어야 한다. 정의가 바뀐 두 해의 차이는 가짜 상승률이다. */
  private static NavigableMap<Integer, Long> usableMedians(List<WageStatSeriesPoint> points) {
    NavigableMap<Integer, Long> usable = new TreeMap<>();
    for (WageStatSeriesPoint point : points) {
      if (point.source() == WageStatSource.KOSIS
          && GROSS_DEFINITION.equals(point.wageDefinition())
          && point.wageMedian() > 0
          && isYear(point.year())) {
        usable.put(Integer.valueOf(point.year()), point.wageMedian());
      }
    }
    return usable;
  }

  private static boolean isYear(String value) {
    return value != null && value.matches("\\d{4}");
  }

  /** 연속 연도 묶음. 두 해 미만 묶음은 상승률을 낼 수 없어 뺀다. */
  private static List<List<Integer>> consecutiveRuns(NavigableMap<Integer, Long> market) {
    List<List<Integer>> runs = new ArrayList<>();
    List<Integer> current = new ArrayList<>();
    for (int year : market.keySet()) {
      if (!current.isEmpty() && current.getLast() + 1 != year) {
        runs.add(current);
        current = new ArrayList<>();
      }
      current.add(year);
    }
    runs.add(current);
    return runs.stream().filter(run -> run.size() >= MIN_POINTS).toList();
  }

  /** 실행을 기록 연도로 잘라 점과 구간을 낸다. 기록이 둘 미만이면 null. */
  private static Run buildRun(
      List<Integer> years,
      NavigableMap<Integer, Long> recorded,
      NavigableMap<Integer, Long> market) {
    NavigableMap<Integer, Long> inRun =
        recorded.subMap(years.getFirst(), true, years.getLast(), true);
    if (inRun.size() < MIN_POINTS) {
      return null;
    }
    int first = inRun.firstKey();
    List<Point> points = new ArrayList<>();
    for (int year = first; year <= inRun.lastKey(); year++) {
      long mine = inRun.floorEntry(year).getValue();
      boolean isRecorded = inRun.containsKey(year);
      points.add(
          new Point(
              String.valueOf(year),
              index(mine, inRun.get(first)),
              index(market.get(year), market.get(first)),
              isRecorded,
              isRecorded ? mine : null,
              market.get(year)));
    }
    return new Run(points, spans(inRun, market));
  }

  private static List<Span> spans(
      NavigableMap<Integer, Long> inRun, NavigableMap<Integer, Long> market) {
    List<Span> spans = new ArrayList<>();
    Integer from = null;
    for (int to : inRun.keySet()) {
      if (from != null) {
        spans.add(
            new Span(
                String.valueOf(from),
                String.valueOf(to),
                growth(inRun.get(to), inRun.get(from)),
                growth(market.get(to), market.get(from))));
      }
      from = to;
    }
    return spans;
  }

  /** 양 끝이 기록 연도라 금액이 항상 있다. */
  private static Latest latest(Run run) {
    Point from = run.points().getFirst();
    Point to = run.points().getLast();
    double mine = growth(to.myGross(), from.myGross());
    double market = growth(to.marketMedian(), from.marketMedian());
    return new Latest(
        from.year(),
        to.year(),
        mine,
        market,
        BigDecimal.valueOf(mine).subtract(BigDecimal.valueOf(market)).doubleValue());
  }

  private static double index(long value, long base) {
    return ratioPercent(value, base).doubleValue();
  }

  private static double growth(long to, long from) {
    return ratioPercent(to - from, from).doubleValue();
  }

  private static BigDecimal ratioPercent(long numerator, long denominator) {
    return BigDecimal.valueOf(numerator)
        .divide(BigDecimal.valueOf(denominator), MathContext.DECIMAL64)
        .multiply(HUNDRED)
        .setScale(1, RoundingMode.HALF_UP);
  }
}
