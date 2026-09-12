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
  @DisplayName("resolveBaseline - 광역 지역 기준선이 존재하면 반환")
  void resolveBaseline_foundRegional() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "MEDIUM");

    given(fallbackResolver.mapExperienceBracket(5)).willReturn("MID");
    given(fallbackResolver.extractBroadRegion("서울 강남구")).willReturn("서울특별시");

    WageStatObservation obs =
        WageStatObservation.builder()
            .wageMedian(60000000L)
            .wageDefinition("ANNUAL_BASE_MEDIAN")
            .statVintage("202607")
            .build();

    given(repository.findLatestBaseline("DEVELOPER", "MID", "서울특별시")).willReturn(Optional.of(obs));

    WageBaseline baseline = resolver.resolveBaseline(profile);

    assertThat(baseline).isNotNull();
    assertThat(baseline.medianGrossSalary()).isEqualTo(60000000L);
    assertThat(baseline.wageDefinition()).isEqualTo("ANNUAL_BASE_MEDIAN");
    assertThat(baseline.statVintage()).isEqualTo("202607");
  }

  @Test
  @DisplayName("resolveBaseline - 광역 지역 없으면 전국 기준 조회 성공")
  void resolveBaseline_fallbackToNational() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch(null, null, null, null, null);

    given(fallbackResolver.mapExperienceBracket(3)).willReturn("JUNIOR");
    given(fallbackResolver.extractBroadRegion(null)).willReturn("서울특별시");

    given(repository.findLatestBaseline("DEVELOPER", "JUNIOR", "서울특별시"))
        .willReturn(Optional.empty());

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

    given(fallbackResolver.mapExperienceBracket(5)).willReturn("MID");
    given(fallbackResolver.extractBroadRegion("서울 강남구")).willReturn("서울특별시");

    given(repository.findLatestBaseline("DEVELOPER", "MID", "서울특별시")).willReturn(Optional.empty());
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
    given(fallbackResolver.mapExperienceBracket(anyInt())).willReturn("JUNIOR");
    given(fallbackResolver.extractBroadRegion(anyString())).willReturn("서울특별시");

    given(repository.findLatestBaseline(anyString(), anyString(), anyString()))
        .willReturn(Optional.empty());

    assertThat(resolver.resolveBaseline(profile)).isNull();
  }
}
