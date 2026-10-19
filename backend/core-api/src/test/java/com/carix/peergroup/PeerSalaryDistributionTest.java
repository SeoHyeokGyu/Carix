package com.carix.peergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.dashboard.service.SalaryDistributionService;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.WageBaseline;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.wagestat.WageStatObservationRepository;
import com.carix.wagestat.WageStatSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 동료집단 공개연봉 p75 (협상 목표 c8). */
class PeerSalaryDistributionTest {

  private static final UUID USER_ID = UUID.randomUUID();

  private static PeerSalaryDistribution withSalaries(int n) {
    // disclosedSalaries 계약은 오름차순이다 — 해석기가 정렬해 넣는다
    List<Long> salaries = LongStream.rangeClosed(1, n).map(i -> i * 1_000_000L).boxed().toList();
    return new PeerSalaryDistribution(salaries, n, 0, "전국");
  }

  @Test
  @DisplayName("공개연봉이 없으면 null")
  void p75_공개연봉없음_null() {
    assertThat(new PeerSalaryDistribution(List.of(), 10, 0, "전국").p75()).isNull();
  }

  /**
   * 연봉 분포 화면(§5.2, B 소유 {@code SalaryDistributionService#buildReference})과 같은 값이어야 한다 — 협상 목표의
   * 「동료집단 상위 25%」가 같은 화면의 p75 와 다르면 안 된다. 5수치는 공개연봉 10건 이상에서만 나오므로 그 이상만 비교한다.
   */
  @ParameterizedTest
  @ValueSource(ints = {10, 11, 17, 40})
  @DisplayName("p75 는 연봉 분포 화면 postingReference.p75 와 같다")
  void p75는_연봉분포화면_postingReference와_같다(int n) {
    PeerSalaryDistribution distribution = withSalaries(n);
    PeerSalaryDistributionResolver resolver = mock(PeerSalaryDistributionResolver.class);
    given(resolver.resolve(any(CareerProfile.class))).willReturn(distribution);
    CareerProfileRepository profiles = mock(CareerProfileRepository.class);
    given(profiles.findByUserId(USER_ID))
        .willReturn(
            Optional.of(
                CareerProfile.builder()
                    .userId(USER_ID)
                    .jobFamily("DEVELOPER")
                    .yearsOfExperience(5)
                    .build()));
    WageBaselineResolver baselines = mock(WageBaselineResolver.class);
    given(baselines.resolveBaseline(any(CareerProfile.class)))
        .willReturn(
            new WageBaseline(60_000_000L, "ANNUAL_GROSS_MEDIAN", "202606", WageStatSource.KOSIS));
    CurrentSalaryProvider salaries = mock(CurrentSalaryProvider.class);
    given(salaries.currentGrossSalary(eq(USER_ID), anyString())).willReturn(Optional.empty());
    SalaryDistributionService service =
        new SalaryDistributionService(
            profiles, baselines, salaries, resolver, mock(WageStatObservationRepository.class));

    Long screenP75 = service.resolve(USER_ID, "202607").postingReference().p75();

    assertThat(distribution.p75()).isNotNull().isEqualTo(screenP75);
  }
}
