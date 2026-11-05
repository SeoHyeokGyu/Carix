package com.carix.dashboard.service;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.index.CurrentSalaryProvider;
import com.carix.index.WageStatSeriesProvider;
import com.carix.index.dto.WageStatSeries;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 어느 임금통계 계열을 읽는가 (기능 명세 US-D8). 계산은 {@link WageGrowthCalculatorTest} 소관이다. */
class WageGrowthServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private CareerProfileRepository profiles;
  private WageStatSeriesProvider series;
  private WageGrowthService service;

  @BeforeEach
  void setUp() {
    profiles = mock(CareerProfileRepository.class);
    series = mock(WageStatSeriesProvider.class);
    CurrentSalaryProvider salaries = mock(CurrentSalaryProvider.class);
    PeerGroupFallbackResolver resolver = mock(PeerGroupFallbackResolver.class);
    // 구간 경계는 대시보드 기준선과 같은 실제 로직이어야 한다
    given(resolver.mapExperienceBracket6(anyInt())).willCallRealMethod();
    given(salaries.recordedYearIncomes(USER_ID)).willReturn(List.of());
    given(series.findSeries(anyString(), anyString()))
        .willReturn(new WageStatSeries("ALL", "MID", "ALL", List.of()));
    service = new WageGrowthService(profiles, salaries, series, resolver);
  }

  private void profile(String jobFamily, Integer years) {
    given(profiles.findByUserId(USER_ID))
        .willReturn(
            Optional.of(
                CareerProfile.builder()
                    .userId(USER_ID)
                    .jobFamily(jobFamily)
                    .yearsOfExperience(years)
                    .build()));
  }

  @Test
  @DisplayName("수집하지 않는 직군은 ALL 계열을 읽는다 — 값이 같으니 결측으로 보이면 안 된다")
  void uncollectedJobFamilyReadsAll() {
    profile("OTHER", 5);

    service.resolve(USER_ID);

    verify(series).findSeries("ALL", "MID");
  }

  @Test
  @DisplayName("프로필이 없으면 대시보드 기준선과 같은 기본값(DEVELOPER, 3년)이다")
  void missingProfileUsesBaselineDefaults() {
    given(profiles.findByUserId(USER_ID)).willReturn(Optional.empty());

    service.resolve(USER_ID);

    verify(series).findSeries("DEVELOPER", "JUNIOR");
  }

  @Test
  @DisplayName("대시보드 기준선과 같은 6구간 경계를 쓴다")
  void usesSameBracketsAsDashboardBaseline() {
    profile("DATA", 8);

    service.resolve(USER_ID);

    verify(series).findSeries("DATA", "SENIOR");
  }
}
