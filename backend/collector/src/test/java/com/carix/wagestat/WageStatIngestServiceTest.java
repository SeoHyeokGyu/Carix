package com.carix.wagestat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WageStatIngestServiceTest {

  private KosisWageStatClient kosisClient;
  private WageStatInterpolator interpolator;
  private WageStatObservationRepository repository;
  private WageStatIngestService service;

  @BeforeEach
  void setUp() {
    kosisClient = mock(KosisWageStatClient.class);
    interpolator = mock(WageStatInterpolator.class);
    repository = mock(WageStatObservationRepository.class);

    service = new WageStatIngestService(kosisClient, interpolator, repository);
  }

  @Test
  @DisplayName("ingestWageStats - KOSIS API 정상 응답 시 보간 후 듀얼 기준선(세전총액, 정액급여) 적재")
  void ingestWageStats_fromKosis() {
    Map<String, Long> gradeDist = Map.of("A01", 100L, "A02", 200L);
    Map<String, Map<String, Long>> yearlyDist = Map.of("2024", gradeDist);

    given(kosisClient.fetchWageDistributionByPeriod(anyString(), any(), any()))
        .willReturn(yearlyDist);
    given(interpolator.calculateAnnualMedianWage(gradeDist)).willReturn(50000000L);
    given(
            repository
                .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .willReturn(Optional.empty());

    int count = service.ingestWageStats("2024", "2024");

    // 6개 연령구간 * 4개 직군 * 2개 기준선(GROSS, BASE) = 48개
    assertThat(count).isEqualTo(48);
    verify(repository, atLeastOnce()).save(any(WageStatObservation.class));
  }

  @Test
  @DisplayName("ingestWageStats - KOSIS 응답 없을 때 기본 fallback 보간값으로 적재")
  void ingestWageStats_fromFallback() {
    given(kosisClient.fetchWageDistributionByPeriod(anyString(), any(), any()))
        .willReturn(Map.of());

    given(
            repository
                .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .willReturn(Optional.empty());

    int count = service.ingestWageStats();

    assertThat(count).isGreaterThan(0);
    verify(repository, atLeastOnce()).save(any(WageStatObservation.class));
  }

  @Test
  @DisplayName("ingestWageStats - 이미 데이터가 존재하면 save를 건너뛴다")
  void ingestWageStats_alreadyExists() {
    given(kosisClient.fetchWageDistributionByPeriod(anyString(), any(), any()))
        .willReturn(Map.of());

    WageStatObservation existing = WageStatObservation.builder().build();
    given(
            repository
                .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .willReturn(Optional.of(existing));

    int count = service.ingestWageStats("2025", "2025");

    assertThat(count).isEqualTo(48);
  }

  @Test
  @DisplayName("ingestWageStats - KOSIS 응답 안에서 보간 실패한 연도만 FALLBACK 으로 기록한다")
  void ingestWageStats_recordsSourcePerYear() {
    Map<String, Long> okDist = Map.of("A01", 100L);
    Map<String, Long> badDist = Map.of("A02", 200L);
    Map<String, Map<String, Long>> yearlyDist = new LinkedHashMap<>();
    yearlyDist.put("2023", okDist);
    yearlyDist.put("2024", badDist);

    given(kosisClient.fetchWageDistributionByPeriod(anyString(), any(), any()))
        .willReturn(yearlyDist);
    given(interpolator.calculateAnnualMedianWage(okDist)).willReturn(50_000_000L);
    given(interpolator.calculateAnnualMedianWage(badDist)).willReturn(0L);
    given(
            repository
                .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .willReturn(Optional.empty());

    service.ingestWageStats("2023", "2024");

    ArgumentCaptor<WageStatObservation> captor = ArgumentCaptor.forClass(WageStatObservation.class);
    verify(repository, atLeastOnce()).save(captor.capture());

    assertThat(captor.getAllValues())
        .filteredOn(o -> "2023".equals(o.getBasePeriod()))
        .isNotEmpty()
        .allMatch(o -> o.getSource() == WageStatSource.KOSIS);
    assertThat(captor.getAllValues())
        .filteredOn(o -> "2024".equals(o.getBasePeriod()))
        .isNotEmpty()
        .allMatch(o -> o.getSource() == WageStatSource.FALLBACK);
  }

  @Test
  @DisplayName("saveOrUpdate - 폴백 상수는 기존 행을 덮지 않는다 (강등 금지)")
  void ingestWageStats_fallbackDoesNotOverwriteExisting() {
    given(kosisClient.fetchWageDistributionByPeriod(anyString(), any(), any()))
        .willReturn(Map.of());

    WageStatObservation existing =
        WageStatObservation.builder()
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition(WageDefinition.ANNUAL_GROSS_MEDIAN)
            .wageMedian(53_400_000L)
            .basePeriod("2025")
            .statVintage("202508")
            .source(WageStatSource.KOSIS)
            .build();
    given(
            repository
                .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                    anyString(), anyString(), anyString(), any(), anyString(), anyString()))
        .willReturn(Optional.of(existing));

    service.ingestWageStats("2025", "2025");

    verify(repository, never()).save(any(WageStatObservation.class));
  }

  @Test
  @DisplayName("saveOrUpdate - KOSIS 실측은 기존 행을 같은 id 로 덮어쓴다 (승격)")
  void ingestWageStats_kosisOverwritesExisting() {
    Map<String, Long> gradeDist = Map.of("A01", 100L);
    Map<String, Map<String, Long>> yearlyDist = Map.of("2024", gradeDist);
    UUID existingId = UUID.randomUUID();

    given(kosisClient.fetchWageDistributionByPeriod(anyString(), any(), any()))
        .willReturn(yearlyDist);
    given(interpolator.calculateAnnualMedianWage(gradeDist)).willReturn(50_000_000L);

    WageStatObservation existing =
        WageStatObservation.builder()
            .id(existingId)
            .jobFamily("DEVELOPER")
            .experienceBracket("MID")
            .region("ALL")
            .wageDefinition(WageDefinition.ANNUAL_GROSS_MEDIAN)
            .wageMedian(51_600_000L)
            .basePeriod("2024")
            .statVintage("202408")
            .source(WageStatSource.FALLBACK)
            .build();

    // GROSS 만 기존 행이 있고 BASE 는 없다. 한 스텁을 모든 조회에 물리면
    // "행마다 제 id 를 재사용" 과 "스텁 하나의 id 를 전부에 박음" 이 구별되지 않는다.
    given(
            repository
                .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                    anyString(),
                    anyString(),
                    anyString(),
                    eq(WageDefinition.ANNUAL_GROSS_MEDIAN),
                    anyString(),
                    anyString()))
        .willReturn(Optional.of(existing));
    given(
            repository
                .findByJobFamilyAndExperienceBracketAndRegionAndWageDefinitionAndBasePeriodAndStatVintage(
                    anyString(),
                    anyString(),
                    anyString(),
                    eq(WageDefinition.ANNUAL_BASE_MEDIAN),
                    anyString(),
                    anyString()))
        .willReturn(Optional.empty());

    service.ingestWageStats("2024", "2024");

    ArgumentCaptor<WageStatObservation> captor = ArgumentCaptor.forClass(WageStatObservation.class);
    verify(repository, atLeastOnce()).save(captor.capture());

    assertThat(captor.getAllValues())
        .isNotEmpty()
        .allMatch(o -> o.getSource() == WageStatSource.KOSIS);
    assertThat(captor.getAllValues())
        .filteredOn(o -> o.getWageDefinition() == WageDefinition.ANNUAL_GROSS_MEDIAN)
        .isNotEmpty()
        .allMatch(o -> existingId.equals(o.getId()));
    assertThat(captor.getAllValues())
        .filteredOn(o -> o.getWageDefinition() == WageDefinition.ANNUAL_BASE_MEDIAN)
        .isNotEmpty()
        .allMatch(o -> !existingId.equals(o.getId()));
  }
}
