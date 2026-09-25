package com.carix.wagestat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.index.dto.WageStatSeries;
import com.carix.index.dto.WageStatSeriesPoint;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultWageStatSeriesProviderTest {

  private WageStatObservationRepository repository;
  private DefaultWageStatSeriesProvider provider;

  @BeforeEach
  void setUp() {
    repository = mock(WageStatObservationRepository.class);
    provider = new DefaultWageStatSeriesProvider(repository);
  }

  @Test
  @DisplayName("직군 또는 경력 브래킷이 null이거나 공백이면 빈 시계열 반환")
  void findSeries_nullOrBlankInput() {
    assertThat(provider.findSeries(null, "MID").points()).isEmpty();
    assertThat(provider.findSeries("   ", "MID").points()).isEmpty();
    assertThat(provider.findSeries("DEVELOPER", null).points()).isEmpty();
    assertThat(provider.findSeries("DEVELOPER", "   ").points()).isEmpty();
  }

  @Test
  @DisplayName("정상 KOSIS 실측 연속 연도에 대해 구간 상승률이 1자리 반올림으로 정확히 산출된다")
  void findSeries_kosisConsecutiveYears_calculatesGrowthRate() {
    WageStatObservation obs2021 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(50_000_000L)
            .basePeriod("2021")
            .statVintage("202108")
            .source(WageStatSource.KOSIS)
            .build();

    WageStatObservation obs2022 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(55_000_000L)
            .basePeriod("2022")
            .statVintage("202208")
            .source(WageStatSource.KOSIS)
            .build();

    WageStatObservation obs2023 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(60_000_000L)
            .basePeriod("2023")
            .statVintage("202308")
            .source(WageStatSource.KOSIS)
            .build();

    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of(obs2021, obs2022, obs2023));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.jobFamily()).isEqualTo("DEVELOPER");
    assertThat(series.experienceBracket()).isEqualTo("MID");
    assertThat(series.region()).isEqualTo("ALL");
    assertThat(series.points()).hasSize(3);

    // 첫 해는 직전 연도가 없으므로 growthRate null
    WageStatSeriesPoint p2021 = series.points().get(0);
    assertThat(p2021.year()).isEqualTo("2021");
    assertThat(p2021.wageMedian()).isEqualTo(50_000_000L);
    assertThat(p2021.growthRatePercent()).isNull();

    // 2022: (55M - 50M) / 50M * 100 = 10.0%
    WageStatSeriesPoint p2022 = series.points().get(1);
    assertThat(p2022.year()).isEqualTo("2022");
    assertThat(p2022.wageMedian()).isEqualTo(55_000_000L);
    assertThat(p2022.growthRatePercent()).isEqualTo(10.0);

    // 2023: (60M - 55M) / 55M * 100 = 9.0909... -> 9.1%
    WageStatSeriesPoint p2023 = series.points().get(2);
    assertThat(p2023.year()).isEqualTo("2023");
    assertThat(p2023.wageMedian()).isEqualTo(60_000_000L);
    assertThat(p2023.growthRatePercent()).isEqualTo(9.1);

    assertThat(series.hasValidKosisGrowthInterval()).isTrue();
  }

  @Test
  @DisplayName("동일 연도에 복수 관측치 존재 시 최우선순위(KOSIS > FALLBACK) 1건만 선택된다")
  void findSeries_deduplicatesByYearWithPriority() {
    WageStatObservation kosis2024 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(65_000_000L)
            .basePeriod("2024")
            .statVintage("202408")
            .source(WageStatSource.KOSIS)
            .build();

    WageStatObservation fallback2024 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(70_000_000L)
            .basePeriod("2024")
            .statVintage("202609")
            .source(WageStatSource.FALLBACK)
            .build();

    // 쿼리 결과는 정렬 순서대로 반환된다 (KOSIS 가 앞서 나옴)
    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of(kosis2024, fallback2024));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.points()).hasSize(1);
    assertThat(series.points().get(0).source()).isEqualTo(WageStatSource.KOSIS);
    assertThat(series.points().get(0).wageMedian()).isEqualTo(65_000_000L);
  }

  @Test
  @DisplayName("어느 한 해라도 실측(KOSIS)이 아니면 해당 구간 상승률은 null 이며 0%로 대체하지 않는다 (§4.9)")
  void findSeries_fallbackOrUnknownExcludedFromGrowth() {
    WageStatObservation obs2021 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(50_000_000L)
            .basePeriod("2021")
            .statVintage("202108")
            .source(WageStatSource.KOSIS)
            .build();

    WageStatObservation obs2022Fallback =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(50_000_000L)
            .basePeriod("2022")
            .statVintage("202208")
            .source(WageStatSource.FALLBACK)
            .build();

    WageStatObservation obs2023Kosis =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(60_000_000L)
            .basePeriod("2023")
            .statVintage("202308")
            .source(WageStatSource.KOSIS)
            .build();

    WageStatObservation obs2024Kosis =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(66_000_000L)
            .basePeriod("2024")
            .statVintage("202408")
            .source(WageStatSource.KOSIS)
            .build();

    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of(obs2021, obs2022Fallback, obs2023Kosis, obs2024Kosis));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.points()).hasSize(4);
    // 2021: 첫 해 -> null
    assertThat(series.points().get(0).growthRatePercent()).isNull();
    // 2022: FALLBACK 포함 구간 -> null (0.0% 아님)
    assertThat(series.points().get(1).growthRatePercent()).isNull();
    // 2023: 직전(2022)이 FALLBACK 이었으므로 구간 제외 -> null
    assertThat(series.points().get(2).growthRatePercent()).isNull();
    // 2024: 직전(2023) KOSIS, 당해(2024) KOSIS -> 10.0%
    assertThat(series.points().get(3).growthRatePercent()).isEqualTo(10.0);
  }

  @Test
  @DisplayName("연도가 연속되지 않고 단절된 경우(Gap) 상승률을 산출하지 않는다")
  void findSeries_nonConsecutiveYears_noGrowthRate() {
    WageStatObservation obs2021 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(50_000_000L)
            .basePeriod("2021")
            .statVintage("202108")
            .source(WageStatSource.KOSIS)
            .build();

    // 2022 누락, 2023 바로 등장
    WageStatObservation obs2023 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(60_000_000L)
            .basePeriod("2023")
            .statVintage("202308")
            .source(WageStatSource.KOSIS)
            .build();

    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of(obs2021, obs2023));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.points()).hasSize(2);
    assertThat(series.points().get(1).growthRatePercent()).isNull();
    assertThat(series.hasValidKosisGrowthInterval()).isFalse();
  }

  @Test
  @DisplayName("ALL 에 데이터가 없으면 전국으로 fallback 조회한다")
  void findSeries_fallbackToNationwide() {
    WageStatObservation obs =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("전국")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(50_000_000L)
            .basePeriod("2024")
            .statVintage("202408")
            .source(WageStatSource.KOSIS)
            .build();

    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of());
    given(repository.findSeriesObservations("DEVELOPER", "MID", "전국", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of(obs));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.region()).isEqualTo("전국");
    assertThat(series.points()).hasSize(1);
  }

  @Test
  @DisplayName("ANNUAL_GROSS_MEDIAN 으로 없으면 wageDefinition=null 전체 fallback 조회한다")
  void findSeries_fallbackToAnyDefinition() {
    WageStatObservation obs =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("OTHER_MEDIAN")
            .wageMedian(50_000_000L)
            .basePeriod("2024")
            .statVintage("202408")
            .source(WageStatSource.UNKNOWN)
            .build();

    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of());
    given(repository.findSeriesObservations("DEVELOPER", "MID", "전국", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of());
    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", null))
        .willReturn(List.of(obs));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.points()).hasSize(1);
    assertThat(series.points().get(0).wageDefinition()).isEqualTo("OTHER_MEDIAN");
  }

  @Test
  @DisplayName("wageDefinition=null 일 때 전국으로 fallback 조회한다")
  void findSeries_fallbackToAnyDefinition_nationwide() {
    WageStatObservation obs =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("전국")
            .wageDefinition("OTHER_MEDIAN")
            .wageMedian(50_000_000L)
            .basePeriod("2024")
            .statVintage("202408")
            .source(WageStatSource.UNKNOWN)
            .build();

    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of());
    given(repository.findSeriesObservations("DEVELOPER", "MID", "전국", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of());
    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", null)).willReturn(List.of());
    given(repository.findSeriesObservations("DEVELOPER", "MID", "전국", null))
        .willReturn(List.of(obs));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.region()).isEqualTo("전국");
    assertThat(series.points()).hasSize(1);
    assertThat(series.points().get(0).wageDefinition()).isEqualTo("OTHER_MEDIAN");
  }

  @Test
  @DisplayName("wageMedian 과 source 가 null 인 경우 안전하게 0L 및 UNKNOWN 으로 대체된다")
  void findSeries_nullWageMedianAndSource() {
    WageStatObservation obs =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageMedian(null)
            .source(null)
            .basePeriod("2024")
            .statVintage("202408")
            .build();

    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of(obs));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.points()).hasSize(1);
    assertThat(series.points().get(0).wageMedian()).isEqualTo(0L);
    assertThat(series.points().get(0).source()).isEqualTo(WageStatSource.UNKNOWN);
  }

  @Test
  @DisplayName("전국 fallback 도 정의 없는 fallback 도 모두 없으면 빈 시계열 반환")
  void findSeries_completelyEmpty() {
    given(repository.findSeriesObservations(anyString(), anyString(), anyString(), any()))
        .willReturn(List.of());

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.points()).isEmpty();
    assertThat(series.hasValidKosisGrowthInterval()).isFalse();
  }

  @Test
  @DisplayName("연도 문자열이 숫자가 아니거나 공백인 경우 안전하게 처리된다")
  void findSeries_nonNumericYear_handledGracefully() {
    WageStatObservation obs1 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageMedian(50_000_000L)
            .basePeriod("BAD_YEAR")
            .source(WageStatSource.KOSIS)
            .build();

    WageStatObservation obs2 =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageMedian(60_000_000L)
            .basePeriod("2024")
            .source(WageStatSource.KOSIS)
            .build();

    given(repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN"))
        .willReturn(List.of(obs1, obs2));

    WageStatSeries series = provider.findSeries("DEVELOPER", "MID");

    assertThat(series.points()).hasSize(2);
    assertThat(series.points().get(1).growthRatePercent()).isNull();
  }
}
