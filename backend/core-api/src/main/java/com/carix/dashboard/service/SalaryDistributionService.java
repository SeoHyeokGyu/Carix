package com.carix.dashboard.service;

import com.carix.dashboard.dto.SalaryDistributionResponse;
import com.carix.datasource.ReadOnlyRouting;
import com.carix.datasource.ReadOnlyRoutingContext;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.WageBaseline;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.peergroup.TrendComparison;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.wagestat.WageStatObservation;
import com.carix.wagestat.WageStatObservationRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
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
@ReadOnlyRouting
public class SalaryDistributionService {

  private static final DateTimeFormatter TREND_MONTH_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM");

  static final String STATUS_OK = "OK";
  static final String STATUS_INSUFFICIENT_SAMPLE = "INSUFFICIENT_SAMPLE";

  private final CareerProfileRepository careerProfileRepository;
  private final WageBaselineResolver wageBaselineResolver;
  private final CurrentSalaryProvider currentSalaryProvider;
  private final PeerSalaryDistributionResolver peerSalaryDistributionResolver;
  private final WageStatObservationRepository wageStatObservationRepository;

  @Autowired
  public SalaryDistributionService(
      CareerProfileRepository careerProfileRepository,
      WageBaselineResolver wageBaselineResolver,
      CurrentSalaryProvider currentSalaryProvider,
      PeerSalaryDistributionResolver peerSalaryDistributionResolver,
      WageStatObservationRepository wageStatObservationRepository) {
    this.careerProfileRepository = careerProfileRepository;
    this.wageBaselineResolver = wageBaselineResolver;
    this.currentSalaryProvider = currentSalaryProvider;
    this.peerSalaryDistributionResolver = peerSalaryDistributionResolver;
    this.wageStatObservationRepository = wageStatObservationRepository;
  }

  /** Compatibility constructor for consumers that do not resolve wage observation data. */
  public SalaryDistributionService(
      CareerProfileRepository careerProfileRepository,
      WageBaselineResolver wageBaselineResolver,
      CurrentSalaryProvider currentSalaryProvider,
      PeerSalaryDistributionResolver peerSalaryDistributionResolver) {
    this(
        careerProfileRepository,
        wageBaselineResolver,
        currentSalaryProvider,
        peerSalaryDistributionResolver,
        null);
  }

  @Transactional(readOnly = true)
  public SalaryDistributionResponse resolve(UUID userId, String baseYearMonth) {
    return resolve(userId, baseYearMonth, null);
  }

  @Transactional(readOnly = true)
  public SalaryDistributionResponse resolve(UUID userId, String baseYearMonth, String year) {
    Optional<CareerProfile> profile = careerProfileRepository.findByUserId(userId);
    if (profile.isEmpty()) {
      // 온보딩 미완료. 동료집단 조건 자체가 없어 표본이 0이다.
      return insufficient(null, 0, null, null, null);
    }

    CareerProfile careerProfile = profile.get();
    WageBaseline baseline;
    PeerSalaryDistribution distribution;
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<WageBaseline> baselineFuture =
          executor.submit(
              () ->
                  ReadOnlyRoutingContext.withReadOnlyCallable(
                          () -> wageBaselineResolver.resolveBaseline(careerProfile))
                      .call());
      Future<PeerSalaryDistribution> distFuture =
          executor.submit(
              () ->
                  ReadOnlyRoutingContext.withReadOnlyCallable(
                          () -> peerSalaryDistributionResolver.resolve(careerProfile))
                      .call());

      baseline = baselineFuture.get();
      distribution = distFuture.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("동료집단 연봉 데이터 병렬 조회 중 인터럽트 발생", e);
    } catch (ExecutionException e) {
      throw new RuntimeException("동료집단 연봉 데이터 병렬 조회 실패", e.getCause());
    }

    if (!distribution.hasSufficientPeers()) {
      return insufficient(
          baseline,
          distribution.peerSampleSize(),
          null,
          distribution.appliedConditions(),
          buildCareerLadder(careerProfile, year));
    }
    if (!distribution.hasSufficientSalaries()) {
      // 공고는 충분한데 공개연봉이 모자라다 — 5수치·사용자 위치는 내지 않고, 화면이 「공고 N건 중 공개 연봉 M건」을 보이게 수와 결측률만 싣는다.
      return insufficient(
          baseline,
          distribution.peerSampleSize(),
          countsOnly(distribution.disclosedSampleSize(), distribution.peerSampleSize()),
          distribution.appliedConditions(),
          buildCareerLadder(careerProfile, year));
    }

    List<Long> disclosed = distribution.disclosedSalaries();
    SalaryDistributionResponse.PostingReference reference =
        buildReference(disclosed, distribution.peerSampleSize());
    List<SalaryDistributionResponse.SalaryTrendPoint> trend = toTrendDtos(distribution.trend());

    return new SalaryDistributionResponse(
        STATUS_OK,
        distribution.peerSampleSize(),
        null,
        toBaselineDto(baseline),
        reference,
        buildUserPosition(userId, baseYearMonth, distribution),
        new SalaryDistributionResponse.DistributionPeerGroup(
            distribution.fallbackLevel(), distribution.appliedConditions()),
        trend,
        buildCareerLadder(careerProfile, year),
        toTrendComparisonDto(distribution.trendComparison()));
  }

  private SalaryDistributionResponse.CareerLadder buildCareerLadder(CareerProfile profile) {
    return buildCareerLadder(profile, null);
  }

  private SalaryDistributionResponse.CareerLadder buildCareerLadder(
      CareerProfile profile, String targetYear) {
    String jobFamily = profile.getJobFamily() == null ? "DEVELOPER" : profile.getJobFamily();
    Map<String, WageStatObservation> observations = new LinkedHashMap<>();
    List<String> availableYears = Collections.emptyList();

    if (wageStatObservationRepository != null) {
      availableYears = wageStatObservationRepository.findDistinctBasePeriodsByJobFamily(jobFamily);
      boolean hasYear = targetYear != null && !targetYear.isBlank();

      for (String bracket : PeerGroupFallbackResolver.BRACKET_6_ORDER) {
        if (hasYear) {
          wageStatObservationRepository
              .findBaselineByYear(jobFamily, bracket, "ALL", targetYear.trim())
              .or(
                  () ->
                      wageStatObservationRepository.findBaselineByYear(
                          jobFamily, bracket, "전국", targetYear.trim()))
              .ifPresent(observation -> observations.put(bracket, observation));
        } else {
          wageStatObservationRepository
              .findLatestBaseline(jobFamily, bracket, "ALL")
              .or(() -> wageStatObservationRepository.findLatestBaseline(jobFamily, bracket, "전국"))
              .ifPresent(observation -> observations.put(bracket, observation));
        }
      }
    }

    String resolvedBasePeriod =
        targetYear != null && !targetYear.isBlank() ? targetYear.trim() : null;
    if (resolvedBasePeriod == null) {
      for (WageStatObservation obs : observations.values()) {
        if (obs.getBasePeriod() != null && !obs.getBasePeriod().isBlank()) {
          resolvedBasePeriod = obs.getBasePeriod();
          break;
        }
      }
      if (resolvedBasePeriod == null && !availableYears.isEmpty()) {
        resolvedBasePeriod = availableYears.get(availableYears.size() - 1);
      }
    }

    List<SalaryDistributionResponse.CareerLadderStep> steps = new ArrayList<>();
    Long previous = null;
    for (String bracket : PeerGroupFallbackResolver.BRACKET_6_ORDER) {
      WageStatObservation observation = observations.get(bracket);
      Long median = observation == null ? null : observation.getWageMedian();
      Double raise =
          previous == null || median == null || previous == 0
              ? null
              : round2((double) (median - previous) / previous * 100);
      steps.add(
          new SalaryDistributionResponse.CareerLadderStep(
              bracket,
              median,
              raise,
              observation == null || observation.getSource() == null
                  ? "UNKNOWN"
                  : observation.getSource().name()));
      previous = median;
    }
    int years = profile.getYearsOfExperience() == null ? 3 : profile.getYearsOfExperience();
    String userBracket =
        years <= 1
            ? "ENTRY"
            : years <= 3 ? "JUNIOR" : years <= 7 ? "MID" : years <= 11 ? "SENIOR" : "LEAD";
    return new SalaryDistributionResponse.CareerLadder(
        userBracket, resolvedBasePeriod, availableYears, List.copyOf(steps));
  }

  private List<SalaryDistributionResponse.SalaryTrendPoint> toTrendDtos(
      List<PeerSalaryDistribution.TrendPoint> trend) {
    if (trend == null || trend.isEmpty()) {
      return Collections.emptyList();
    }
    return trend.stream()
        .map(
            t ->
                new SalaryDistributionResponse.SalaryTrendPoint(
                    t.yearMonth(),
                    t.median(),
                    t.sampleSize(),
                    t.disclosedSampleSize(),
                    t.missingRate()))
        .toList();
  }

  private SalaryDistributionResponse.TrendComparison toTrendComparisonDto(
      TrendComparison comparison) {
    if (comparison == null) {
      return null;
    }
    return new SalaryDistributionResponse.TrendComparison(
        toTrendPeriodDto(comparison.recent()),
        toTrendPeriodDto(comparison.previous()),
        comparison.verdict().name(),
        comparison.sources(),
        comparison.inProgress().stream().map(TREND_MONTH_FORMAT::format).toList());
  }

  private SalaryDistributionResponse.TrendPeriod toTrendPeriodDto(TrendComparison.Period period) {
    return new SalaryDistributionResponse.TrendPeriod(
        TREND_MONTH_FORMAT.format(period.from()),
        TREND_MONTH_FORMAT.format(period.to()),
        period.median(),
        period.ciLow(),
        period.ciHigh(),
        period.disclosedSampleSize());
  }

  /** 공개연봉 부족 표본 부족 응답용 — 5수치 없이 공개연봉 수와 결측률만. */
  private SalaryDistributionResponse.PostingReference countsOnly(int disclosed, int totalPostings) {
    double missingRate =
        totalPostings == 0
            ? 100.0
            : round2((double) (totalPostings - disclosed) / totalPostings * 100);
    return new SalaryDistributionResponse.PostingReference(
        null, null, null, null, null, disclosed, missingRate);
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
      WageBaseline baseline,
      int sampleSize,
      SalaryDistributionResponse.PostingReference postingReference,
      String appliedConditions,
      SalaryDistributionResponse.CareerLadder careerLadder) {
    return new SalaryDistributionResponse(
        STATUS_INSUFFICIENT_SAMPLE,
        sampleSize,
        PeerGroupFallbackResolver.MIN_SAMPLE_SIZE,
        baseline == null ? null : toBaselineDto(baseline),
        postingReference,
        null,
        appliedConditions == null
            ? null
            : new SalaryDistributionResponse.DistributionPeerGroup(null, appliedConditions),
        Collections.emptyList(),
        careerLadder);
  }

  private SalaryDistributionResponse.WageBaseline toBaselineDto(WageBaseline baseline) {
    return new SalaryDistributionResponse.WageBaseline(
        "AGE_BAND_PROXY",
        baseline.source() == null ? "UNKNOWN" : baseline.source().name(),
        baseline.medianGrossSalary(),
        baseline.wageDefinition());
  }

  private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }
}
