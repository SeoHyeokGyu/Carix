package com.carix.peergroup;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.JobPosting;
import com.carix.peergroup.TrendComparison.Verdict;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SalaryTrendComparatorTest {

  private static final long MAN = 10_000L;
  private static final long TEN_MILLION = 1_000 * MAN;
  private static final int MIN_SAMPLE = 10;
  private static final YearMonth LATEST = YearMonth.of(2026, 9);
  private static final YearMonth CURRENT = YearMonth.of(2026, 10);
  private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

  private static final Map<String, Set<YearMonth>> FULL_COVERAGE =
      Map.of("JOBKOREA", months(YearMonth.of(2026, 4), 6));

  private static Set<YearMonth> months(YearMonth from, int count) {
    return IntStream.range(0, count).mapToObj(from::plusMonths).collect(Collectors.toSet());
  }

  private static JobPosting posting(String source, YearMonth month, Long salary) {
    return JobPosting.builder()
        .source(source)
        .sampledMonth(month == null ? null : YYYYMM.format(month))
        .salaryMin(salary)
        .salaryMax(salary)
        .build();
  }

  private static JobPosting reversed(String source, YearMonth month, long salary) {
    return JobPosting.builder()
        .source(source)
        .sampledMonth(YYYYMM.format(month))
        .salaryMin(salary)
        .salaryMax(salary)
        .postedStartDate(LocalDate.of(2026, 5, 10))
        .postedEndDate(LocalDate.of(2026, 5, 1))
        .build();
  }

  private static List<JobPosting> spread(List<Long> salaries, YearMonth firstMonth) {
    List<JobPosting> list = new ArrayList<>();
    for (int i = 0; i < salaries.size(); i++) {
      list.add(posting("JOBKOREA", firstMonth.plusMonths(i % 3), salaries.get(i)));
    }
    return list;
  }

  private static List<Long> flat(int n, long base) {
    return IntStream.range(0, n).mapToObj(i -> base + i * MAN).toList();
  }

  private static List<JobPosting> both(List<Long> previous, List<Long> recent) {
    List<JobPosting> all = new ArrayList<>(spread(previous, YearMonth.of(2026, 4)));
    all.addAll(spread(recent, YearMonth.of(2026, 7)));
    return all;
  }

  private static TrendComparison compare(List<JobPosting> postings) {
    return SalaryTrendComparator.compare(postings, LATEST, FULL_COVERAGE, CURRENT, MIN_SAMPLE);
  }

  @Test
  void medianCiRanks_table() {
    assertThat(SalaryTrendComparator.medianCiRanks(10)).containsExactly(2, 9);
    assertThat(SalaryTrendComparator.medianCiRanks(14)).containsExactly(3, 12);
    assertThat(SalaryTrendComparator.medianCiRanks(22)).containsExactly(6, 17);
    assertThat(SalaryTrendComparator.medianCiRanks(0)).isNull();
    assertThat(SalaryTrendComparator.medianCiRanks(3)).containsExactly(1, 3);
  }

  @Test
  void windowsFromLatestSampled() {
    TrendComparison result = compare(List.of());

    assertThat(result.recent().from()).isEqualTo(YearMonth.of(2026, 7));
    assertThat(result.recent().to()).isEqualTo(YearMonth.of(2026, 9));
    assertThat(result.previous().from()).isEqualTo(YearMonth.of(2026, 4));
    assertThat(result.previous().to()).isEqualTo(YearMonth.of(2026, 6));
    assertThat(result.inProgress()).containsExactly(YearMonth.of(2026, 10));
  }

  @Test
  void inProgressSpansTwoMonths() {
    TrendComparison result =
        SalaryTrendComparator.compare(
            List.of(), LATEST, FULL_COVERAGE, YearMonth.of(2026, 11), MIN_SAMPLE);

    assertThat(result.inProgress()).containsExactly(YearMonth.of(2026, 10), YearMonth.of(2026, 11));
    assertThat(result.recent().from()).isEqualTo(YearMonth.of(2026, 7));
  }

  @Test
  void noInProgressWhenCurrentMonthSampled() {
    TrendComparison result =
        SalaryTrendComparator.compare(
            List.of(),
            YearMonth.of(2026, 10),
            Map.of("JOBKOREA", months(YearMonth.of(2026, 5), 6)),
            YearMonth.of(2026, 10),
            MIN_SAMPLE);

    assertThat(result.inProgress()).isEmpty();
  }

  @Test
  void windowsCrossYear() {
    TrendComparison result =
        SalaryTrendComparator.compare(
            List.of(), YearMonth.of(2027, 2), Map.of(), YearMonth.of(2027, 3), MIN_SAMPLE);

    assertThat(result.recent().from()).isEqualTo(YearMonth.of(2026, 12));
    assertThat(result.recent().to()).isEqualTo(YearMonth.of(2027, 2));
    assertThat(result.previous().from()).isEqualTo(YearMonth.of(2026, 9));
    assertThat(result.previous().to()).isEqualTo(YearMonth.of(2026, 11));
  }

  @Test
  void nullWhenNoBackfill() {
    assertThat(SalaryTrendComparator.compare(List.of(), null, FULL_COVERAGE, CURRENT, MIN_SAMPLE))
        .isNull();
  }

  @Test
  void excludesNonSampledAndReversed() {
    List<JobPosting> postings =
        new ArrayList<>(spread(flat(10, 50 * TEN_MILLION / 10), YearMonth.of(2026, 7)));
    for (int i = 0; i < 5; i++) {
      postings.add(posting("JOBKOREA", null, 50_000_000L));
    }
    for (int i = 0; i < 3; i++) {
      postings.add(reversed("JOBKOREA", YearMonth.of(2026, 8), 50_000_000L));
    }

    assertThat(compare(postings).recent().disclosedSampleSize()).isEqualTo(10);
  }

  @Test
  void pooledMedian() {
    List<Long> salaries = IntStream.rangeClosed(1, 11).mapToObj(i -> i * TEN_MILLION).toList();

    TrendComparison result = compare(spread(salaries, YearMonth.of(2026, 7)));

    assertThat(result.recent().median()).isEqualTo(6 * TEN_MILLION);
  }

  @Test
  void insufficientWhenEitherBelowMin() {
    TrendComparison result = compare(both(flat(9, 40_000_000L), flat(10, 60_000_000L)));

    assertThat(result.verdict()).isEqualTo(Verdict.INSUFFICIENT_SAMPLE);
    assertThat(result.previous().median()).isNull();
    assertThat(result.previous().ciLow()).isNull();
    assertThat(result.previous().ciHigh()).isNull();
    assertThat(result.previous().disclosedSampleSize()).isEqualTo(9);
    assertThat(result.recent().median()).isNotNull();
  }

  @Test
  void noSampledRowsInPeerGroup() {
    List<JobPosting> postings = List.of(posting("JOBKOREA", null, 50_000_000L));

    TrendComparison result = compare(postings);

    assertThat(result.recent().disclosedSampleSize()).isZero();
    assertThat(result.previous().disclosedSampleSize()).isZero();
    assertThat(result.verdict()).isEqualTo(Verdict.INSUFFICIENT_SAMPLE);
  }

  @Test
  void upWhenIntervalsSeparate() {
    assertThat(compare(both(flat(14, 40_000_000L), flat(14, 60_000_000L))).verdict())
        .isEqualTo(Verdict.UP);
  }

  @Test
  void downWhenIntervalsSeparate() {
    assertThat(compare(both(flat(14, 60_000_000L), flat(14, 40_000_000L))).verdict())
        .isEqualTo(Verdict.DOWN);
  }

  @Test
  void flatWhenIntervalsOverlap() {
    assertThat(compare(both(flat(14, 50_000_000L), flat(14, 50_000_000L))).verdict())
        .isEqualTo(Verdict.FLAT);
  }

  @Test
  void dropsSourceMissingAMonth() {
    Map<String, Set<YearMonth>> coverage =
        Map.of(
            "JOBKOREA", months(YearMonth.of(2026, 4), 6),
            "SARAMIN", months(YearMonth.of(2026, 6), 4));
    List<JobPosting> postings =
        new ArrayList<>(spread(flat(10, 50_000_000L), YearMonth.of(2026, 7)));
    for (int i = 0; i < 4; i++) {
      postings.add(posting("SARAMIN", YearMonth.of(2026, 8), 50_000_000L));
    }

    TrendComparison result =
        SalaryTrendComparator.compare(postings, LATEST, coverage, CURRENT, MIN_SAMPLE);

    assertThat(result.sources()).containsExactly("JOBKOREA");
    assertThat(result.recent().disclosedSampleSize()).isEqualTo(10);
  }

  @Test
  void noCommonSource() {
    Map<String, Set<YearMonth>> coverage =
        Map.of(
            "JOBKOREA", months(YearMonth.of(2026, 4), 3),
            "SARAMIN", months(YearMonth.of(2026, 7), 3));

    TrendComparison result =
        SalaryTrendComparator.compare(
            spread(flat(14, 50_000_000L), YearMonth.of(2026, 7)),
            LATEST,
            coverage,
            CURRENT,
            MIN_SAMPLE);

    assertThat(result.sources()).isEmpty();
    assertThat(result.verdict()).isEqualTo(Verdict.INSUFFICIENT_SAMPLE);
  }

  @Test
  void monthWithoutAnyBackfillCountsAsZeroRows() {
    Set<YearMonth> fiveMonths = months(YearMonth.of(2026, 4), 6);
    fiveMonths.remove(YearMonth.of(2026, 5));
    List<JobPosting> postings = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      postings.add(posting("JOBKOREA", YearMonth.of(2026, 4), 50_000_000L + i));
      postings.add(posting("JOBKOREA", YearMonth.of(2026, 6), 50_000_000L + i));
    }
    postings.addAll(spread(flat(12, 50_000_000L), YearMonth.of(2026, 7)));

    TrendComparison result =
        SalaryTrendComparator.compare(
            postings, LATEST, Map.of("JOBKOREA", fiveMonths), CURRENT, MIN_SAMPLE);

    assertThat(result.sources()).containsExactly("JOBKOREA");
    assertThat(result.previous().disclosedSampleSize()).isEqualTo(24);
    assertThat(result.recent().disclosedSampleSize()).isEqualTo(12);
  }
}
