package com.carix.dashboard.service;

import com.carix.dashboard.dto.WageGrowthResponse;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.WageStatSeriesProvider;
import com.carix.index.dto.RecordedYearIncome;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 연봉과 같은 경력 구간 임금의 누적 상승을 합성한다 (기능 명세 US-D8). 결정 신호·지수·스냅샷에는 연결하지 않고, 금액을 저장하거나 로그에 남기지 않는다.
 *
 * <p>시장 계열은 지금 내 경력 구간에 고정한다. 구간과 직군 기본값은 {@code WageBaselineResolver} 와 같다.
 */
@Service
@RequiredArgsConstructor
public class WageGrowthService {

  private static final String DEFAULT_JOB_FAMILY = "DEVELOPER";
  private static final int DEFAULT_YEARS = 3;
  private static final String ALL_JOB_FAMILIES = "ALL";

  /** 수집기가 임금통계를 넣는 직군. 모두 같은 KOSIS 값이라 그 밖의 직군은 공통 계열을 읽는다. */
  private static final Set<String> COLLECTED_JOB_FAMILIES =
      Set.of(DEFAULT_JOB_FAMILY, "DATA", "AI", ALL_JOB_FAMILIES);

  private final CareerProfileRepository careerProfileRepository;
  private final CurrentSalaryProvider currentSalaryProvider;
  private final WageStatSeriesProvider wageStatSeriesProvider;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;

  @Transactional(readOnly = true)
  public WageGrowthResponse resolve(UUID userId) {
    CareerProfile profile =
        careerProfileRepository.findByUserId(userId).orElseGet(() -> new CareerProfile(userId));
    List<RecordedYearIncome> incomes = currentSalaryProvider.recordedYearIncomes(userId);
    return WageGrowthCalculator.calculate(
        incomes, wageStatSeriesProvider.findSeries(seriesJobFamily(profile), bracket(profile)));
  }

  private static String seriesJobFamily(CareerProfile profile) {
    String jobFamily = profile.getJobFamily() != null ? profile.getJobFamily() : DEFAULT_JOB_FAMILY;
    return COLLECTED_JOB_FAMILIES.contains(jobFamily) ? jobFamily : ALL_JOB_FAMILIES;
  }

  private String bracket(CareerProfile profile) {
    int years =
        profile.getYearsOfExperience() != null ? profile.getYearsOfExperience() : DEFAULT_YEARS;
    return peerGroupFallbackResolver.mapExperienceBracket6(years);
  }
}
