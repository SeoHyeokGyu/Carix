package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.index.dto.WageBaseline;
import com.carix.profile.domain.CareerProfile;
import com.carix.wagestat.WageStatObservation;
import com.carix.wagestat.WageStatObservationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WageBaselineResolverTest {

  private WageStatObservationRepository repository;
  private PeerGroupFallbackResolver fallbackResolver;
  private WageBaselineResolver resolver;

  @BeforeEach
  void setUp() {
    repository = mock(WageStatObservationRepository.class);
    fallbackResolver = mock(PeerGroupFallbackResolver.class);
    resolver = new WageBaselineResolver(repository, fallbackResolver);
  }

  @Test
  @DisplayName("resolveBaseline - profile이 null이면 null 반환")
  void resolveBaseline_nullProfile() {
    assertThat(resolver.resolveBaseline(null)).isNull();
  }

  @Test
  @DisplayName("resolveBaseline - 전국 기준선이 존재하면 반환")
  void resolveBaseline_foundNational() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    given(fallbackResolver.mapExperienceBracket6(5)).willReturn("MID");

    WageStatObservation obs =
        WageStatObservation.builder()
            .wageMedian(60000000L)
            .wageDefinition("ANNUAL_BASE_MEDIAN")
            .statVintage("202607")
            .source(com.carix.wagestat.WageStatSource.KOSIS)
            .build();

    given(repository.findLatestBaseline("DEVELOPER", "MID", "전국")).willReturn(Optional.of(obs));

    WageBaseline baseline = resolver.resolveBaseline(profile);

    assertThat(baseline).isNotNull();
    assertThat(baseline.medianGrossSalary()).isEqualTo(60000000L);
    assertThat(baseline.wageDefinition()).isEqualTo("ANNUAL_BASE_MEDIAN");
    assertThat(baseline.statVintage()).isEqualTo("202607");
    assertThat(baseline.source()).isEqualTo(com.carix.wagestat.WageStatSource.KOSIS);
  }

  @Test
  @DisplayName("resolveBaseline - 신입(0년) ENTRY 브래킷 기준선 조회 성공")
  void resolveBaseline_entryBracket() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 0, "서울 강남구", "FULL_TIME", "MEDIUM");

    given(fallbackResolver.mapExperienceBracket6(0)).willReturn("ENTRY");

    WageStatObservation obs =
        WageStatObservation.builder()
            .wageMedian(31200000L)
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .statVintage("202508")
            .build();

    given(repository.findLatestBaseline("DEVELOPER", "ENTRY", "ALL")).willReturn(Optional.of(obs));

    WageBaseline baseline = resolver.resolveBaseline(profile);

    assertThat(baseline).isNotNull();
    assertThat(baseline.medianGrossSalary()).isEqualTo(31200000L);
  }

  @Test
  @DisplayName("resolveBaseline - 13년차 LEAD 브래킷 기준선 조회 성공")
  void resolveBaseline_leadBracket() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 13, "서울 강남구", "FULL_TIME", "LARGE");

    given(fallbackResolver.mapExperienceBracket6(13)).willReturn("LEAD");

    WageStatObservation obs =
        WageStatObservation.builder()
            .wageMedian(74400000L)
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .statVintage("202508")
            .build();

    given(repository.findLatestBaseline("DEVELOPER", "LEAD", "ALL")).willReturn(Optional.of(obs));

    WageBaseline baseline = resolver.resolveBaseline(profile);

    assertThat(baseline).isNotNull();
    assertThat(baseline.medianGrossSalary()).isEqualTo(74400000L);
  }

  @Test
  @DisplayName("resolveBaseline - 프로필 값이 비면 기본 직무·경력으로 전국 기준 조회")
  void resolveBaseline_defaultsToNational() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch(null, null, null, null, null);

    given(fallbackResolver.mapExperienceBracket6(3)).willReturn("JUNIOR");

    WageStatObservation obs =
        WageStatObservation.builder()
            .wageMedian(45000000L)
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .statVintage("202606")
            .build();

    given(repository.findLatestBaseline("DEVELOPER", "JUNIOR", "전국")).willReturn(Optional.of(obs));

    WageBaseline baseline = resolver.resolveBaseline(profile);

    assertThat(baseline).isNotNull();
    assertThat(baseline.medianGrossSalary()).isEqualTo(45000000L);
  }

  @Test
  @DisplayName("resolveBaseline - 전국 기준도 없으면 ALL 코드로 조회 성공")
  void resolveBaseline_fallbackToAll() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE");

    given(fallbackResolver.mapExperienceBracket6(5)).willReturn("MID");

    given(repository.findLatestBaseline("DEVELOPER", "MID", "전국")).willReturn(Optional.empty());

    WageStatObservation obs =
        WageStatObservation.builder()
            .wageMedian(47690057L)
            .wageDefinition("ANNUAL_GROSS_MEDIAN")
            .statVintage("202508")
            .build();

    given(repository.findLatestBaseline("DEVELOPER", "MID", "ALL")).willReturn(Optional.of(obs));

    WageBaseline baseline = resolver.resolveBaseline(profile);

    assertThat(baseline).isNotNull();
    assertThat(baseline.medianGrossSalary()).isEqualTo(47690057L);
    assertThat(baseline.statVintage()).isEqualTo("202508");
  }

  @Test
  @DisplayName("resolveBaseline - 전국 기준도 없으면 null 반환")
  void resolveBaseline_notFound() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    given(fallbackResolver.mapExperienceBracket6(anyInt())).willReturn("JUNIOR");

    given(repository.findLatestBaseline(anyString(), anyString(), anyString()))
        .willReturn(Optional.empty());

    assertThat(resolver.resolveBaseline(profile)).isNull();
  }
}
