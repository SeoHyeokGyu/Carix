package com.carix.wagestat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WageStatObservationRepositoryTest {

  @Autowired private WageStatObservationRepository repository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("findLatestBaseline - KOSIS 실측이 vintage 가 더 큰 FALLBACK 상수를 이기고 우선 조회된다")
  void findLatestBaseline_sourcePriority() {
    // 2025년 기준: KOSIS 실측(202508) vs 벽시계 FALLBACK 상수(202609)
    WageStatObservation kosis =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(60_000_000L)
            .basePeriod("2025")
            .statVintage("202508")
            .source(WageStatSource.KOSIS)
            .build();

    WageStatObservation fallback =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(50_000_000L)
            .basePeriod("2025")
            .statVintage("202609") // vintage 가 더 큼
            .source(WageStatSource.FALLBACK)
            .build();

    entityManager.persist(fallback);
    entityManager.persist(kosis);
    entityManager.flush();

    Optional<WageStatObservation> result = repository.findLatestBaseline("DEVELOPER", "MID", "ALL");

    assertThat(result).isPresent();
    assertThat(result.get().getSource()).isEqualTo(WageStatSource.KOSIS);
    assertThat(result.get().getWageMedian()).isEqualTo(60_000_000L);
    assertThat(result.get().getStatVintage()).isEqualTo("202508");
  }

  @Test
  @DisplayName("findLatestBaseline - 동일 연도/출처에서 ANNUAL_GROSS_MEDIAN 이 우선 선택된다")
  void findLatestBaseline_grossMedianPriority() {
    WageStatObservation baseMedian =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_BASE_MEDIAN")
            .wageMedian(45_000_000L)
            .basePeriod("2025")
            .statVintage("202508")
            .source(WageStatSource.KOSIS)
            .build();

    WageStatObservation grossMedian =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(60_000_000L)
            .basePeriod("2025")
            .statVintage("202508")
            .source(WageStatSource.KOSIS)
            .build();

    entityManager.persist(baseMedian);
    entityManager.persist(grossMedian);
    entityManager.flush();

    Optional<WageStatObservation> result = repository.findLatestBaseline("DEVELOPER", "MID", "ALL");

    assertThat(result).isPresent();
    assertThat(result.get().getWageDefinition()).isEqualTo("ANNUAL_GROSS_MEDIAN");
    assertThat(result.get().getWageMedian()).isEqualTo(60_000_000L);
  }

  @Test
  @DisplayName("findSeriesObservations - 연도 오름차순, 동일 연도 내 KOSIS 우선순위 정렬로 조회된다")
  void findSeriesObservations_orderingAndFiltering() {
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
            .wageMedian(52_000_000L)
            .basePeriod("2022")
            .statVintage("202209")
            .source(WageStatSource.FALLBACK)
            .build();

    WageStatObservation obs2022Kosis =
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

    WageStatObservation otherFamily =
        WageStatObservation.builder()
            .jobFamily("DESIGNER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .wageMedian(40_000_000L)
            .basePeriod("2023")
            .statVintage("202308")
            .source(WageStatSource.KOSIS)
            .build();

    entityManager.persist(obs2021);
    entityManager.persist(obs2022Fallback);
    entityManager.persist(obs2022Kosis);
    entityManager.persist(obs2023);
    entityManager.persist(otherFamily);
    entityManager.flush();

    List<WageStatObservation> series =
        repository.findSeriesObservations("DEVELOPER", "MID", "ALL", "ANNUAL_GROSS_MEDIAN");

    assertThat(series).hasSize(4);
    assertThat(series.get(0).getBasePeriod()).isEqualTo("2021");
    // 2022년 두 개 중 KOSIS 가 FALLBACK 보다 앞에 와야 함
    assertThat(series.get(1).getBasePeriod()).isEqualTo("2022");
    assertThat(series.get(1).getSource()).isEqualTo(WageStatSource.KOSIS);
    assertThat(series.get(2).getBasePeriod()).isEqualTo("2022");
    assertThat(series.get(2).getSource()).isEqualTo(WageStatSource.FALLBACK);
    assertThat(series.get(3).getBasePeriod()).isEqualTo("2023");
  }
}
