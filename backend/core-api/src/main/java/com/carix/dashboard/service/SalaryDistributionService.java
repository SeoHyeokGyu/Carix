package com.carix.dashboard.service;

import com.carix.dashboard.dto.SalaryDistributionResponse;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.WageBaseline;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동료집단 연봉 분포 (API 규격 §5.2).
 *
 * <p>분포 산출 자체는 {@link PeerSalaryDistributionResolver} 가 한다 — 시나리오 시뮬레이터의 실현 가능성(§6.1)도 같은 분포를 읽기
 * 때문이다. 여기 남은 것은 §5.2 응답 고유의 매핑, 즉 5수치와 사용자 위치다.
 *
 * <p>표본이 모자라면 예외를 던지지 않고 {@code INSUFFICIENT_SAMPLE} 과 표본 수를 돌려준다. 규격이 204 를 금지한다 — 화면이 `표본 부족`과 표본
 * 수를 함께 보여줘야 하기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class SalaryDistributionService {

  static final String STATUS_OK = "OK";
  static final String STATUS_INSUFFICIENT_SAMPLE = "INSUFFICIENT_SAMPLE";

  private static final String BASELINE_SOURCE = "WAGE_STAT";

  private final CareerProfileRepository careerProfileRepository;
  private final WageBaselineResolver wageBaselineResolver;
  private final CurrentSalaryProvider currentSalaryProvider;
  private final PeerSalaryDistributionResolver peerSalaryDistributionResolver;

  @Transactional(readOnly = true)
  public SalaryDistributionResponse resolve(UUID userId, String baseYearMonth) {
    Optional<CareerProfile> profile = careerProfileRepository.findByUserId(userId);
    if (profile.isEmpty()) {
      // 온보딩 미완료. 동료집단 조건 자체가 없어 표본이 0이다.
      return insufficient(null, 0, null);
    }

    CareerProfile careerProfile = profile.get();
    WageBaseline baseline = wageBaselineResolver.resolveBaseline(careerProfile);
    PeerSalaryDistribution distribution = peerSalaryDistributionResolver.resolve(careerProfile);

    if (!distribution.hasSufficientPeers()) {
      return insufficient(
          baseline, distribution.peerSampleSize(), distribution.appliedConditions());
    }

    List<Long> disclosed = distribution.disclosedSalaries();
    SalaryDistributionResponse.PostingReference reference =
        buildReference(disclosed, distribution.peerSampleSize());

    return new SalaryDistributionResponse(
        STATUS_OK,
        distribution.peerSampleSize(),
        null,
        toBaselineDto(baseline),
        reference,
        buildUserPosition(userId, baseYearMonth, distribution),
        new SalaryDistributionResponse.DistributionPeerGroup(
            distribution.fallbackLevel(), distribution.appliedConditions()));
  }

  /**
   * 정렬된 값에서 5수치를 뽑는다. {@code percentile_cont} 대신 nearest-rank 를 쓴다 — collector 의 집계와 같은 방식이라야 두 경로의
   * 숫자가 어긋나지 않고, H2 와 Postgres 의 분위수 함수 차이도 타지 않는다.
   */
  private SalaryDistributionResponse.PostingReference buildReference(
      List<Long> sorted, int totalPostings) {

    if (sorted.isEmpty()) {
      return new SalaryDistributionResponse.PostingReference(
          null, null, null, null, null, 0, 100.0);
    }
    int n = sorted.size();
    double missingRate = round2((double) (totalPostings - n) / totalPostings * 100);

    return new SalaryDistributionResponse.PostingReference(
        sorted.get(0),
        sorted.get((int) Math.floor(n * 0.25)),
        sorted.get((int) Math.floor(n * 0.50)),
        sorted.get(Math.min(n - 1, (int) Math.floor(n * 0.75))),
        sorted.get(n - 1),
        n,
        missingRate);
  }

  /**
   * 사용자의 분포 내 위치. 연봉 이력이 없으면 {@code null} 이다 — 임의 값으로 메우지 않는다 ([MVP 규격 §13] 데이터가 없는 기능을 추정값으로 채워
   * 출시하지 않는다).
   */
  private SalaryDistributionResponse.UserPosition buildUserPosition(
      UUID userId, String baseYearMonth, PeerSalaryDistribution distribution) {

    Optional<CurrentSalary> salary =
        currentSalaryProvider.currentGrossSalary(userId, baseYearMonth);
    if (salary.isEmpty() || distribution.disclosedSalaries().isEmpty()) {
      return null;
    }
    long gross = salary.get().grossSalary();

    return new SalaryDistributionResponse.UserPosition(gross, distribution.percentileOf(gross));
  }

  // --- 헬퍼 ---

  private SalaryDistributionResponse insufficient(
      WageBaseline baseline, int sampleSize, String appliedConditions) {
    return new SalaryDistributionResponse(
        STATUS_INSUFFICIENT_SAMPLE,
        sampleSize,
        PeerGroupFallbackResolver.MIN_SAMPLE_SIZE,
        baseline == null ? null : toBaselineDto(baseline),
        null,
        null,
        appliedConditions == null
            ? null
            : new SalaryDistributionResponse.DistributionPeerGroup(null, appliedConditions));
  }

  private SalaryDistributionResponse.WageBaseline toBaselineDto(WageBaseline baseline) {
    return new SalaryDistributionResponse.WageBaseline(
        BASELINE_SOURCE, baseline.medianGrossSalary(), baseline.wageDefinition());
  }

  private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
