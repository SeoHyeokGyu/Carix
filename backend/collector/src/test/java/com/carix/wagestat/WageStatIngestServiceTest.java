package com.carix.wagestat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
