package com.carix.dashboard.service;

import com.carix.dashboard.dto.SalaryDistributionResponse;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.dto.WageBaseline;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동료집단 연봉 분포 (API 규격 §5.2).
 *
 * <p>산출원은 원 공고({@code job_posting}) 직접 집계다. {@code market_aggregate} 는 원천별로 5수치만 담아 결합 분포와 백분위를
 * 재구성할 수 없다. 대신 {@code dedup_group_id} 중복 제거와 유효 공개연봉 필터를 여기서 직접 건다 — 정의는 collector 의 {@code
 * MarketAggregateService} 와 같다.
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
  private final JobPostingRepository jobPostingRepository;
  private final WageBaselineResolver wageBaselineResolver;
  private final CurrentSalaryProvider currentSalaryProvider;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;

  @Transactional(readOnly = true)
  public SalaryDistributionResponse resolve(UUID userId, String baseYearMonth) {
    Optional<CareerProfile> profile = careerProfileRepository.findByUserId(userId);
    if (profile.isEmpty()) {
      // 온보딩 미완료. 동료집단 조건 자체가 없어 표본이 0이다.
      return insufficient(null, 0, null);
    }

    CareerProfile careerProfile = profile.get();
    WageBaseline baseline = wageBaselineResolver.resolveBaseline(careerProfile);
    Resolved resolved = resolveWithFallback(careerProfile);

    if (resolved.postings().size() < PeerGroupFallbackResolver.MIN_SAMPLE_SIZE) {
      return insufficient(baseline, resolved.postings().size(), resolved.appliedConditions());
    }

    List<Long> disclosed = disclosedSalaries(resolved.postings());
    SalaryDistributionResponse.PostingReference reference =
        buildReference(disclosed, resolved.postings().size());

    return new SalaryDistributionResponse(
        STATUS_OK,
        resolved.postings().size(),
        null,
        toBaselineDto(baseline),
        reference,
        buildUserPosition(userId, baseYearMonth, disclosed),
        new SalaryDistributionResponse.DistributionPeerGroup(
            resolved.level(), resolved.appliedConditions()));
  }

  // --- 완화 ---

  /**
   * 5단계 완화를 원 공고 위에서 돌린다. 단계 정의는 {@link PeerGroupFallbackResolver} 와 같고 조회 대상만 다르다.
   *
   * <p>지역은 {@code extractBroadRegion} 이 아니라 첫 토큰을 접두사로 쓴다. 그 메서드는 {@code market_aggregate.region} 의
   * 정규화된 값({@code "서울특별시"})을 만드는데, 원 공고의 {@code region} 은 {@code "서울 강남구"} 형태라 접두사가 맞아야 걸린다.
   */
  private Resolved resolveWithFallback(CareerProfile profile) {
    String jobFamily = orDefault(profile.getJobFamily(), "DEVELOPER");
    int years = profile.getYearsOfExperience() != null ? profile.getYearsOfExperience() : 3;
    String bracket = peerGroupFallbackResolver.mapExperienceBracket(years);
    String detailRegion = orDefault(profile.getRegion(), "서울 강남구");
    String broadPrefix = broadRegionPrefix(detailRegion);
    String employmentType = orDefault(profile.getEmploymentType(), "FULL_TIME");
    String companySize = profile.getCompanySize();

    int[] range = bracketYearRange(bracket);
    int[] widened = bracketYearRange(peerGroupFallbackResolver.getAdjacentBrackets(bracket));

    List<Resolved> ladder = new ArrayList<>();
    if (companySize != null && !companySize.isBlank()) {
      ladder.add(
          attempt(
              1,
              jobFamily,
              detailRegion,
              employmentType,
              companySize,
              range,
              detailRegion,
              bracket));
    }
    ladder.add(
        attempt(2, jobFamily, detailRegion, employmentType, null, range, detailRegion, bracket));
    ladder.add(
        attempt(3, jobFamily, broadPrefix, employmentType, null, range, broadPrefix, bracket));
    ladder.add(
        attempt(
            4, jobFamily, broadPrefix, employmentType, null, widened, broadPrefix, bracket + "±1"));
    ladder.add(
        attempt(5, jobFamily, broadPrefix, null, null, widened, broadPrefix, bracket + "±1"));

    for (Resolved candidate : ladder) {
      if (candidate.postings().size() >= PeerGroupFallbackResolver.MIN_SAMPLE_SIZE) {
        return candidate;
      }
    }
    // 모든 단계에서 부족하면 가장 넓은 단계의 표본 수를 그대로 알린다.
    return ladder.get(ladder.size() - 1);
  }

  private Resolved attempt(
      int level,
      String jobFamily,
      String regionPrefix,
      String employmentType,
      String companySize,
      int[] yearRange,
      String regionLabel,
      String bracketLabel) {

    List<JobPosting> candidates =
        jobPostingRepository.findPeerGroupCandidates(
            jobFamily, regionPrefix, employmentType, companySize, yearRange[0], yearRange[1]);

    return new Resolved(
        level,
        dedupe(candidates),
        "%s / 경력 %s / %s".formatted(regionLabel, bracketLabel, companySizeLabel(companySize)));
  }

  /** {@code dedup_group_id} 당 대표 1건만 남긴다 (collector 와 같은 규약 — 먼저 만난 행이 대표다). */
  private List<JobPosting> dedupe(List<JobPosting> postings) {
    Map<UUID, JobPosting> byGroup = new LinkedHashMap<>();
    for (JobPosting posting : postings) {
      byGroup.putIfAbsent(posting.dedupKey(), posting);
    }
    return new ArrayList<>(byGroup.values());
  }

  // --- 분포 ---

  private List<Long> disclosedSalaries(List<JobPosting> postings) {
    List<Long> values = new ArrayList<>();
    for (JobPosting posting : postings) {
      Long salary = posting.disclosedSalary();
      if (salary != null) {
        values.add(salary);
      }
    }
    Collections.sort(values);
    return values;
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
      UUID userId, String baseYearMonth, List<Long> sorted) {

    Optional<CurrentSalary> salary =
        currentSalaryProvider.currentGrossSalary(userId, baseYearMonth);
    if (salary.isEmpty() || sorted.isEmpty()) {
      return null;
    }
    long gross = salary.get().grossSalary();
    long below = sorted.stream().filter(value -> value < gross).count();
    int percentile = (int) Math.round((double) below / sorted.size() * 100);

    return new SalaryDistributionResponse.UserPosition(gross, percentile);
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

  /** 브래킷의 연 단위 범위. {@link PeerGroupFallbackResolver#mapExperienceBracket} 의 경계와 같다. */
  private int[] bracketYearRange(String bracket) {
    return switch (bracket) {
      case "JUNIOR" -> new int[] {0, 3};
      case "SENIOR" -> new int[] {8, 99};
      default -> new int[] {4, 7};
    };
  }

  private int[] bracketYearRange(List<String> brackets) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (String bracket : brackets) {
      int[] range = bracketYearRange(bracket);
      min = Math.min(min, range[0]);
      max = Math.max(max, range[1]);
    }
    return new int[] {min, max};
  }

  private String broadRegionPrefix(String detailRegion) {
    String trimmed = detailRegion.trim();
    int space = trimmed.indexOf(' ');
    return space > 0 ? trimmed.substring(0, space) : trimmed;
  }

  private String companySizeLabel(String companySize) {
    return companySize == null || companySize.isBlank() ? "회사규모 무관" : companySize;
  }

  private String orDefault(String value, String fallback) {
    return (value != null && !value.isBlank()) ? value : fallback;
  }

  private double round2(double value) {
    return Math.round(value * 100.0) / 100.0;
  }

  private record Resolved(int level, List<JobPosting> postings, String appliedConditions) {}
}
