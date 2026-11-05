package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.dashboard.dto.WageGrowthResponse;
import com.carix.dashboard.dto.WageGrowthResponse.Latest;
import com.carix.dashboard.dto.WageGrowthResponse.Point;
import com.carix.dashboard.dto.WageGrowthResponse.Span;
import com.carix.dashboard.dto.WageGrowthResponse.Status;
import com.carix.index.dto.RecordedYearIncome;
import com.carix.index.dto.WageStatSeries;
import com.carix.index.dto.WageStatSeriesPoint;
import com.carix.wagestat.WageStatSource;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 상승률 비교 (기능 명세 US-D8·§4.9). */
class WageGrowthCalculatorTest {

  private static final String GROSS = "ANNUAL_GROSS_MEDIAN";

  private static WageStatSeriesPoint point(
      String year, long median, String definition, WageStatSource source) {
    return new WageStatSeriesPoint(year, median, definition, source, year + "08", null);
  }

  private static WageStatSeriesPoint kosis(String year, long median) {
    return point(year, median, GROSS, WageStatSource.KOSIS);
  }

  /** 2021~2025 전 구간 실측. 지수 100, 103.4, 106.4, 110.2, 113.3. */
  private static List<WageStatSeriesPoint> fullSeries() {
    return List.of(
        kosis("2021", 10_000_000L),
        kosis("2022", 10_340_000L),
        kosis("2023", 10_640_000L),
        kosis("2024", 11_020_000L),
        kosis("2025", 11_330_000L));
  }

  /** 2023 만 상수라 2021~22 와 2024~25 두 실행으로 갈린다. */
  private static List<WageStatSeriesPoint> splitSeries() {
    return List.of(
        kosis("2021", 10_000_000L),
        kosis("2022", 10_340_000L),
        point("2023", 10_340_000L, GROSS, WageStatSource.FALLBACK),
        kosis("2024", 11_020_000L),
        kosis("2025", 11_330_000L));
  }

  private static RecordedYearIncome income(int year, long gross) {
    return new RecordedYearIncome(year, gross);
  }

  /** 금액이 결과와 무관한 테스트용 — 해마다 오르는 단순한 계열이다. */
  private static List<RecordedYearIncome> incomes(int... years) {
    return Arrays.stream(years)
        .mapToObj(y -> income(y, 40_000_000L + (y - 2000) * 400_000L))
        .toList();
  }

  private static WageGrowthResponse calculate(
      List<RecordedYearIncome> incomes, List<WageStatSeriesPoint> points) {
    return WageGrowthCalculator.calculate(
        incomes, new WageStatSeries("DEVELOPER", "MID", "ALL", points));
  }

  private static final List<RecordedYearIncome> MOCKUP_INCOMES =
      List.of(
          income(2021, 40_000_000L),
          income(2022, 42_000_000L),
          income(2023, 42_500_000L),
          income(2025, 45_000_000L));

  @Test
  @DisplayName("누적 상승률과 격차를 낸다")
  void cumulativeAndGap() {
    WageGrowthResponse response = calculate(MOCKUP_INCOMES, fullSeries());

    assertThat(response.status()).isEqualTo(Status.OK);
    assertThat(response.bracket()).isEqualTo("MID");
    assertThat(response.basis()).isEqualTo("AGE_BAND_PROXY");
    assertThat(response.source()).isEqualTo("KOSIS");
    assertThat(response.latest()).isEqualTo(new Latest("2021", "2025", 12.5, 13.3, -0.8));
  }

  @Test
  @DisplayName("기록 사이 빈 해는 시장을 합친 한 행이다 — 2년치 내 변화를 1년치 시장과 견주면 틀린다")
  void gapYearMergesIntoOneSpan() {
    List<Span> spans = calculate(MOCKUP_INCOMES, fullSeries()).runs().getFirst().spans();

    assertThat(spans).contains(new Span("2023", "2025", 5.9, 6.5));
    assertThat(spans).hasSize(3);
  }

  @Test
  @DisplayName("기록 사이 빈 해의 점은 직전 지수를 유지하되 금액을 내지 않는다")
  void gapYearPointHasIndexButNoAmount() {
    List<Point> points = calculate(MOCKUP_INCOMES, fullSeries()).runs().getFirst().points();
    Point gap = points.stream().filter(p -> p.year().equals("2024")).findFirst().orElseThrow();

    assertThat(gap.recorded()).isFalse();
    assertThat(gap.myGross()).isNull();
    assertThat(gap.myIndex()).isEqualTo(106.3);
    assertThat(gap.marketIndex()).isEqualTo(110.2);
    assertThat(points.getFirst().myIndex()).isEqualTo(100.0);
    assertThat(points.getFirst().myGross()).isEqualTo(40_000_000L);
  }

  @Test
  @DisplayName("기록되고 값이 같은 해는 0.0% 다 — 기록된 사실이다")
  void recordedSameValueIsZero() {
    WageGrowthResponse response =
        calculate(List.of(income(2021, 40_000_000L), income(2022, 40_000_000L)), fullSeries());

    assertThat(response.latest().myGrowthPercent()).isEqualTo(0.0);
    assertThat(response.runs().getFirst().spans().getFirst().myGrowthPercent()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("마지막 기록 뒤로 이어 그리지 않는다 — 입력만 안 한 사람이 뒤처져 보이면 안 된다")
  void doesNotExtendPastLastRecord() {
    WageGrowthResponse response = calculate(incomes(2021, 2023), fullSeries());

    assertThat(response.status()).isEqualTo(Status.OK);
    assertThat(response.runs().getFirst().points().getLast().year()).isEqualTo("2023");
    assertThat(response.latest().toYear()).isEqualTo("2023");
    assertThat(response.lastRecordedYear()).isEqualTo("2023");
    assertThat(response.latestStatYear()).isEqualTo("2025");
  }

  @Test
  @DisplayName("첫 KOSIS 이전 기록의 이어 온 값으로 시작하지 않는다")
  void doesNotStartFromCarriedValue() {
    List<WageStatSeriesPoint> series =
        List.of(
            kosis("2020", 9_800_000L),
            kosis("2021", 10_000_000L),
            kosis("2022", 10_340_000L),
            kosis("2023", 10_640_000L),
            kosis("2024", 11_020_000L),
            kosis("2025", 11_330_000L));

    WageGrowthResponse response = calculate(incomes(2018, 2022, 2024), series);

    assertThat(response.latest().fromYear()).isEqualTo("2022");
    assertThat(response.latest().toYear()).isEqualTo("2024");
  }

  @Test
  @DisplayName("실행 안 기록이 하나면 NO_OVERLAP 이다")
  void singleRecordInRunIsNoOverlap() {
    List<WageStatSeriesPoint> series2020 =
        List.of(
            kosis("2020", 9_800_000L),
            kosis("2021", 10_000_000L),
            kosis("2022", 10_340_000L),
            kosis("2023", 10_640_000L),
            kosis("2024", 11_020_000L),
            kosis("2025", 11_330_000L));

    assertThat(calculate(incomes(2018, 2022), series2020).status()).isEqualTo(Status.NO_OVERLAP);

    WageGrowthResponse afterStat = calculate(incomes(2024, 2026), fullSeries());
    assertThat(afterStat.status()).isEqualTo(Status.NO_OVERLAP);
    assertThat(afterStat.latest()).isNull();
    assertThat(afterStat.runs()).isEmpty();
    assertThat(afterStat.latestStatYear()).isEqualTo("2025");
    assertThat(afterStat.lastRecordedYear()).isEqualTo("2026");
  }

  @Test
  @DisplayName("FALLBACK 연도에서 끊기고 다음 실행은 100 에서 다시 시작한다")
  void fallbackYearSplitsRuns() {
    WageGrowthResponse response = calculate(incomes(2021, 2022, 2024, 2025), splitSeries());

    assertThat(response.runs()).hasSize(2);
    Point restart = response.runs().get(1).points().getFirst();
    assertThat(restart.year()).isEqualTo("2024");
    assertThat(restart.myIndex()).isEqualTo(100.0);
    assertThat(restart.marketIndex()).isEqualTo(100.0);
    assertThat(response.latest().fromYear()).isEqualTo("2024");
    assertThat(response.latest().toYear()).isEqualTo("2025");
  }

  @Test
  @DisplayName("오래된 실행에만 기록이 충분하면 그 실행이 latest 다")
  void onlyOldRunSurvives() {
    WageGrowthResponse response = calculate(incomes(2021, 2022, 2025), splitSeries());

    assertThat(response.runs()).hasSize(1);
    assertThat(response.latest().fromYear()).isEqualTo("2021");
    assertThat(response.latest().toYear()).isEqualTo("2022");
    assertThat(response.lastRecordedYear()).isEqualTo("2025");
    assertThat(response.latestStatYear()).isEqualTo("2025");
  }

  @Test
  @DisplayName("UNKNOWN 연도와 빠진 연도도 실행을 끊는다")
  void unknownAndMissingYearsSplitRuns() {
    List<WageStatSeriesPoint> unknown =
        List.of(
            kosis("2021", 10_000_000L),
            kosis("2022", 10_340_000L),
            point("2023", 10_640_000L, GROSS, WageStatSource.UNKNOWN),
            kosis("2024", 11_020_000L),
            kosis("2025", 11_330_000L));
    List<WageStatSeriesPoint> missing =
        List.of(
            kosis("2021", 10_000_000L),
            kosis("2022", 10_340_000L),
            kosis("2024", 11_020_000L),
            kosis("2025", 11_330_000L));
    int[] years = {2021, 2022, 2024, 2025};

    assertThat(calculate(incomes(years), unknown).runs()).hasSize(2);
    assertThat(calculate(incomes(years), missing).runs()).hasSize(2);
  }

  @Test
  @DisplayName("다른 정의와 0 중앙값 연도에서 끊긴다 — 정의가 바뀐 두 해의 차이는 가짜 상승률이다")
  void otherDefinitionAndZeroMedianSplitRuns() {
    List<WageStatSeriesPoint> base =
        List.of(
            kosis("2021", 10_000_000L),
            kosis("2022", 10_340_000L),
            point("2023", 8_880_000L, "ANNUAL_BASE_MEDIAN", WageStatSource.KOSIS),
            kosis("2024", 11_020_000L),
            kosis("2025", 11_330_000L));
    List<WageStatSeriesPoint> zero =
        List.of(
            kosis("2021", 10_000_000L),
            kosis("2022", 10_340_000L),
            kosis("2023", 0L),
            kosis("2024", 11_020_000L),
            kosis("2025", 11_330_000L));
    int[] years = {2021, 2022, 2023, 2024, 2025};

    assertThat(calculate(incomes(years), base).runs()).hasSize(2);
    assertThat(calculate(incomes(years), zero).runs()).hasSize(2);
  }

  @Test
  @DisplayName("누적은 반올림 상승률이 아니라 원값에서 나온다")
  void cumulativeComesFromRawValues() {
    List<WageStatSeriesPoint> wrongRates =
        fullSeries().stream()
            .map(
                p ->
                    new WageStatSeriesPoint(
                        p.year(),
                        p.wageMedian(),
                        p.wageDefinition(),
                        p.source(),
                        p.statVintage(),
                        99.9))
            .toList();

    assertThat(calculate(MOCKUP_INCOMES, wrongRates).latest().marketGrowthPercent())
        .isEqualTo(13.3);
  }

  @Test
  @DisplayName("격차는 표시된 두 값의 차다 — 화면의 숫자끼리 맞아떨어져야 한다")
  void gapIsDifferenceOfDisplayedValues() {
    // 내 12.46 → 12.5, 시장 13.34 → 13.3. 원값 차는 -0.88 이라 -0.9 지만 화면은 -0.8 이어야 한다
    List<WageStatSeriesPoint> series =
        List.of(kosis("2021", 10_000_000L), kosis("2022", 11_334_000L));
    List<RecordedYearIncome> mine = List.of(income(2021, 10_000_000L), income(2022, 11_246_000L));

    assertThat(calculate(mine, series).latest().gapPp()).isEqualTo(-0.8);
  }

  @Test
  @DisplayName("상태 우선순위 — 사용자가 할 수 있는 일이 있는 쪽을 먼저 말한다")
  void statusPriority() {
    WageGrowthResponse noBoth = calculate(incomes(2023), List.of());
    assertThat(noBoth.status()).isEqualTo(Status.NO_INCOME_HISTORY);
    assertThat(noBoth.latestStatYear()).isNull();
    assertThat(noBoth.lastRecordedYear()).isEqualTo("2023");

    assertThat(calculate(incomes(2022, 2023), List.of()).status()).isEqualTo(Status.NO_WAGE_STAT);

    List<WageStatSeriesPoint> notConsecutive =
        List.of(kosis("2021", 10_000_000L), kosis("2023", 10_640_000L));
    assertThat(calculate(incomes(2021, 2023), notConsecutive).status())
        .isEqualTo(Status.NO_WAGE_STAT);
  }

  @Test
  @DisplayName("Point 의 toString 은 금액을 드러내지 않는다 (완료 기준 10)")
  void pointToStringMasksAmount() {
    Point point = new Point("2023", 100.0, 100.0, true, 40_000_000L, 10_000_000L);

    assertThat(point.toString()).contains("2023").doesNotContain("40000000");
  }
}
