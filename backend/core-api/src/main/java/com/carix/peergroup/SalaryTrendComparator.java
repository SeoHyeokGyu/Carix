package com.carix.peergroup;

import com.carix.jobposting.JobPosting;
import com.carix.peergroup.TrendComparison.Period;
import com.carix.peergroup.TrendComparison.Verdict;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 동료집단 공고의 최근·이전 3개월 제시 연봉 중앙값을 무작위 표본 프레임에서 비교한다.
 *
 * <p>두 기간 6개월 중 어느 원천에든 표본이 있는 달을 모두 채운 원천만 센다. 어느 원천에도 표본이 없는 달은 0건으로 본다. 월 구성이 다른 원천을 섞으면 프레임이
 * 달라져 추세가 아니라 구성 변화를 읽게 된다. 중앙값의 95% 신뢰구간은 정확 이항(p=0.5) 순위로 구하고, 두 구간이 겹치지 않을 때만 방향을 말한다.
 */
public final class SalaryTrendComparator {

  static final int WINDOW_MONTHS = 3;
  private static final BigDecimal CONFIDENCE = new BigDecimal("0.95");

  private SalaryTrendComparator() {}

  /** latestSampled 가 null 이면 null. */
  public static TrendComparison compare(
      List<JobPosting> peerPostings,
      YearMonth latestSampled,
      Map<String, Set<YearMonth>> sampledMonthsBySource,
      YearMonth currentMonth,
      int minSample) {
    if (latestSampled == null) {
      return null;
    }
    YearMonth recentFrom = latestSampled.minusMonths(WINDOW_MONTHS - 1);
    YearMonth previousTo = recentFrom.minusMonths(1);
    YearMonth previousFrom = previousTo.minusMonths(WINDOW_MONTHS - 1);

    Set<YearMonth> backfilled =
        sampledMonthsBySource.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    Set<YearMonth> required =
        IntStream.range(0, WINDOW_MONTHS * 2)
            .mapToObj(previousFrom::plusMonths)
            .filter(backfilled::contains)
            .collect(Collectors.toSet());
    List<String> sources =
        sampledMonthsBySource.entrySet().stream()
            .filter(e -> e.getValue().containsAll(required))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();

    long[] recent = salaries(peerPostings, sources, recentFrom, latestSampled);
    long[] previous = salaries(peerPostings, sources, previousFrom, previousTo);
    Period recentPeriod = period(recentFrom, latestSampled, recent, minSample);
    Period previousPeriod = period(previousFrom, previousTo, previous, minSample);

    return new TrendComparison(
        recentPeriod,
        previousPeriod,
        verdict(recentPeriod, previousPeriod),
        sources,
        inProgress(latestSampled, currentMonth));
  }

  /** 1부터 센 [하한 순위, 상한 순위]. n < 1 이면 null. */
  static int[] medianCiRanks(int n) {
    if (n < 1) {
      return null;
    }
    BigInteger total = BigInteger.TWO.pow(n);
    BigInteger threshold =
        CONFIDENCE.multiply(new BigDecimal(total)).setScale(0, RoundingMode.CEILING).toBigInteger();
    BigInteger[] choose = binomialRow(n);
    BigInteger[] prefix = new BigInteger[n + 2];
    prefix[0] = BigInteger.ZERO;
    for (int i = 0; i <= n; i++) {
      prefix[i + 1] = prefix[i].add(choose[i]);
    }
    for (int k = n / 2; k >= 1; k--) {
      BigInteger covered = prefix[n - k + 1].subtract(prefix[k]);
      if (covered.compareTo(threshold) >= 0) {
        return new int[] {k, n - k + 1};
      }
    }
    return new int[] {1, n};
  }

  private static BigInteger[] binomialRow(int n) {
    BigInteger[] row = new BigInteger[n + 1];
    row[0] = BigInteger.ONE;
    for (int i = 1; i <= n; i++) {
      row[i] = row[i - 1].multiply(BigInteger.valueOf(n - i + 1)).divide(BigInteger.valueOf(i));
    }
    return row;
  }

  private static long[] salaries(
      List<JobPosting> postings, List<String> sources, YearMonth from, YearMonth to) {
    return postings.stream()
        .filter(p -> !p.isReversed() && sources.contains(p.getSource()))
        .filter(
            p -> {
              YearMonth month = p.sampledYearMonth();
              return month != null && !month.isBefore(from) && !month.isAfter(to);
            })
        .map(JobPosting::disclosedSalary)
        .filter(Objects::nonNull)
        .mapToLong(Long::longValue)
        .sorted()
        .toArray();
  }

  private static Period period(YearMonth from, YearMonth to, long[] sorted, int minSample) {
    int n = sorted.length;
    if (n < minSample || n < 1) {
      return new Period(from, to, null, null, null, n);
    }
    int[] ranks = medianCiRanks(n);
    return new Period(from, to, sorted[n / 2], sorted[ranks[0] - 1], sorted[ranks[1] - 1], n);
  }

  private static Verdict verdict(Period recent, Period previous) {
    if (recent.median() == null || previous.median() == null) {
      return Verdict.INSUFFICIENT_SAMPLE;
    }
    if (recent.ciLow() > previous.ciHigh()) {
      return Verdict.UP;
    }
    if (recent.ciHigh() < previous.ciLow()) {
      return Verdict.DOWN;
    }
    return Verdict.FLAT;
  }

  private static List<YearMonth> inProgress(YearMonth latestSampled, YearMonth currentMonth) {
    List<YearMonth> months = new ArrayList<>();
    for (YearMonth m = latestSampled.plusMonths(1); !m.isAfter(currentMonth); m = m.plusMonths(1)) {
      months.add(m);
    }
    return List.copyOf(months);
  }
}
