package com.carix.peergroup;

import com.carix.config.IndexThresholdProperties;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.SourceMonth;
import com.carix.profile.domain.CareerProfile;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원 공고({@code job_posting}) 위에서 동료집단 5단계 완화를 돌려 연봉 분포를 만든다.
 *
 * <p>{@code market_aggregate} 를 쓰지 않는 이유는 그쪽이 원천별로 5수치만 담아 결합 분포와 백분위를 재구성할 수 없어서다. 대신 {@code
 * dedup_group_id} 중복 제거와 유효 공개연봉 필터를 여기서 직접 건다 — 정의는 collector 의 {@code MarketAggregateService} 와
 * 같다.
 *
 * <p>단계 정의는 {@link PeerGroupFallbackResolver} 와 같고 조회 대상만 다르다.
 */
@Service
@RequiredArgsConstructor
public class PeerSalaryDistributionResolver {

  private static final DateTimeFormatter TREND_YM_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM");
  private static final DateTimeFormatter SAMPLED_MONTH_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMM");
  private static final int COMPARISON_WINDOW_MONTHS = SalaryTrendComparator.WINDOW_MONTHS * 2;
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  /** "N년 이상" 공고를 구간에 넣는 여유. {@link #fitsBracket} 참고. */
  private static final int OPEN_ENDED_TOLERANCE_YEARS = 1;

  private final JobPostingRepository jobPostingRepository;
  private final PeerGroupFallbackResolver peerGroupFallbackResolver;

  @Autowired(required = false)
  private IndexThresholdProperties thresholds;

  @Autowired(required = false)
  private Clock clock;

  /** 테스트에서 현재 월을 고정하려고 필드 주입 자리에 값을 넣는다. */
  void setClock(Clock clock) {
    this.clock = clock;
  }

  public record ResolvedPeerPostings(
      int level, List<JobPosting> postings, String appliedConditions) {}

  /** 프로필의 동료집단 완화 결과 공고 목록을 반환한다 (스킬 추천 co-occurrence 용 — 공고 수만 본다). */
  @Transactional(readOnly = true)
  public ResolvedPeerPostings resolvePostings(CareerProfile profile) {
    int minSample = minSample();
    return settle(ladder(profile), candidate -> candidate.postings().size() >= minSample);
  }

  /** 완화 단계 1~5 의 후보. 회사규모가 없으면 1단계를 건너뛴다. */
  private List<ResolvedPeerPostings> ladder(CareerProfile profile) {
    String jobFamily = orDefault(profile.getJobFamily(), "DEVELOPER");
    int years = profile.getYearsOfExperience() != null ? profile.getYearsOfExperience() : 3;
    String bracket = peerGroupFallbackResolver.mapExperienceBracket(years);
    String detailRegion = postingRegion(orDefault(profile.getRegion(), "서울 강남구"));
    String broadPrefix = broadRegionPrefix(detailRegion);
    String employmentType = orDefault(profile.getEmploymentType(), "FULL_TIME");
    String companySize = profile.getCompanySize();

    int[] range = bracketYearRange(bracket);
    int[] widened = bracketYearRange(peerGroupFallbackResolver.getAdjacentBrackets(bracket));

    List<ResolvedPeerPostings> ladder = new ArrayList<>();
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
    return ladder;
  }

  private int minSample() {
    return thresholds != null
        ? thresholds.getPeerGroupMinSampleSize()
        : PeerGroupFallbackResolver.MIN_SAMPLE_SIZE;
  }

  /** 기준을 채우는 첫 단계. 어느 단계도 못 채우면 가장 넓은 단계. */
  private static ResolvedPeerPostings settle(
      List<ResolvedPeerPostings> ladder, Predicate<ResolvedPeerPostings> sufficient) {
    for (ResolvedPeerPostings candidate : ladder) {
      if (sufficient.test(candidate)) {
        return candidate;
      }
    }
    return ladder.get(ladder.size() - 1);
  }

  /**
   * 프로필의 동료집단 분포. 공고 수와 공개연봉 수가 둘 다 최소 표본을 채우는 단계에 안착한다 — 공고만 넘고 공개연봉이 모자라면 한 단계 더 넓힌다. 어느 단계에서도 못
   * 채우면 가장 넓은 단계의 결과를 그대로 돌려준다 — 호출자가 {@link PeerSalaryDistribution#hasSufficientPeers()}·{@link
   * PeerSalaryDistribution#hasSufficientSalaries()} 로 판단한다.
   */
  @Transactional(readOnly = true)
  public PeerSalaryDistribution resolve(CareerProfile profile) {
    int minSample = minSample();
    return toDistribution(
        settle(
            ladder(profile),
            candidate ->
                candidate.postings().size() >= minSample
                    && disclosedSalaries(candidate.postings()).size() >= minSample
                    && disclosedCompanyCount(candidate.postings()) >= minSample),
        minSample);
  }

  private PeerSalaryDistribution toDistribution(ResolvedPeerPostings resolved, int minSample) {
    List<Long> disclosed = disclosedSalaries(resolved.postings());
    int companies = disclosedCompanyCount(resolved.postings());
    boolean sufficient =
        resolved.postings().size() >= minSample
            && disclosed.size() >= minSample
            && companies >= minSample;
    return new PeerSalaryDistribution(
        disclosed,
        resolved.postings().size(),
        resolved.level(),
        resolved.appliedConditions(),
        calculateTrend(resolved.postings()),
        sufficient ? compareTrend(resolved.postings()) : null,
        companies);
  }

  /**
   * 무작위 표본 프레임 기준 최근·이전 3개월 비교. 표본 월이 없으면 null.
   *
   * <p>NOTE: (cjkang) 원천 간 중복 묶음에서 대표가 표본이 아닌 행이면 그 묶음의 표본 행은 빠진다. 알려진 한계로 두고 고치지 않았다.
   */
  private TrendComparison compareTrend(List<JobPosting> postings) {
    Clock effective = clock != null ? clock : Clock.system(SEOUL);
    YearMonth currentMonth = YearMonth.now(effective.withZone(SEOUL));
    YearMonth latest =
        jobPostingRepository
            .findLatestSampledMonth(currentMonth.format(SAMPLED_MONTH_FORMAT))
            .map(this::parseSampledMonth)
            .orElse(null);
    if (latest == null) {
      return null;
    }
    YearMonth windowFrom = latest.minusMonths(COMPARISON_WINDOW_MONTHS - 1);
    Map<String, Set<YearMonth>> monthsBySource = new LinkedHashMap<>();
    for (SourceMonth sourceMonth :
        jobPostingRepository.findSampledSourceMonths(
            windowFrom.format(SAMPLED_MONTH_FORMAT), latest.format(SAMPLED_MONTH_FORMAT))) {
      YearMonth month = parseSampledMonth(sourceMonth.sampledMonth());
      if (month != null) {
        monthsBySource.computeIfAbsent(sourceMonth.source(), k -> new TreeSet<>()).add(month);
      }
    }
    return SalaryTrendComparator.compare(
        postings, latest, monthsBySource, currentMonth, minSample());
  }

  /** 형식이 깨진 값은 null — 한 행 때문에 요청이 실패하지 않게 한다. */
  private YearMonth parseSampledMonth(String value) {
    try {
      return YearMonth.parse(value, SAMPLED_MONTH_FORMAT);
    } catch (java.time.format.DateTimeParseException | NullPointerException e) {
      return null;
    }
  }

  /** 동료집단 공고의 등록월(posted_start_date)별 중앙 제시 연봉 추이를 집계한다 (기능 명세 §4.6). */
  private List<PeerSalaryDistribution.TrendPoint> calculateTrend(List<JobPosting> postings) {
    if (postings == null || postings.isEmpty()) {
      return Collections.emptyList();
    }

    Map<String, List<JobPosting>> byMonth = new java.util.TreeMap<>();
    for (JobPosting posting : postings) {
      if (posting.getPostedStartDate() == null || posting.isReversed()) {
        continue;
      }
      String ym = posting.getPostedStartDate().format(TREND_YM_FORMATTER);
      byMonth.computeIfAbsent(ym, k -> new ArrayList<>()).add(posting);
    }

    List<PeerSalaryDistribution.TrendPoint> result = new ArrayList<>();
    for (Map.Entry<String, List<JobPosting>> entry : byMonth.entrySet()) {
      String ym = entry.getKey();
      List<JobPosting> monthPostings = entry.getValue();
      int sampleSize = monthPostings.size();
      List<Long> salaries = disclosedSalaries(monthPostings);
      int disclosedSampleSize = salaries.size();
      double missingRate =
          Math.round((double) (sampleSize - disclosedSampleSize) / sampleSize * 1000.0) / 10.0;

      Long median = null;
      int minSample =
          thresholds != null
              ? thresholds.getPeerGroupMinSampleSize()
              : PeerGroupFallbackResolver.MIN_SAMPLE_SIZE;
      if (disclosedSampleSize >= minSample) {
        median = salaries.get((int) Math.floor(disclosedSampleSize * 0.50));
      }

      result.add(
          new PeerSalaryDistribution.TrendPoint(
              ym, median, sampleSize, disclosedSampleSize, missingRate));
    }
    return result;
  }

  private ResolvedPeerPostings attempt(
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

    return new ResolvedPeerPostings(
        level,
        dedupe(candidates).stream().filter(p -> fitsBracket(p, yearRange[0])).toList(),
        "%s / 경력 %s / %s".formatted(regionLabel, bracketLabel, companySizeLabel(companySize)));
  }

  /**
   * {@code dedup_group_id} 당 대표 1건만 남긴다 — 대표는 {@link JobPosting#REPRESENTATIVE_ORDER} 의 첫
   * 행(collector 와 같은 규약).
   */
  private List<JobPosting> dedupe(List<JobPosting> postings) {
    Map<UUID, JobPosting> byGroup = new LinkedHashMap<>();
    List<JobPosting> ordered = new ArrayList<>(postings);
    ordered.sort(JobPosting.REPRESENTATIVE_ORDER);
    for (JobPosting posting : ordered) {
      byGroup.putIfAbsent(posting.dedupKey(), posting);
    }
    return new ArrayList<>(byGroup.values());
  }

  /**
   * 최대 경력이 없는 공고는 "N년 이상" 이다. N 이 구간 하한보다 1년 넘게 낮으면 신입·저연차를 받는 공고라 그 연봉이 구간을 대표하지 않는다 — 겹침 조건(쿼리)만
   * 보면 이런 공고가 모든 상위 구간에 걸려 분포를 끌어내린다. 1년 여유는 MID(4~7년)의 「3년 이상」처럼 구간에 붙은 공고를 남기려는 것이다 — 운영 실측에서 이를
   * 빼면 추이 비교 표본이 기간당 7건으로 줄었다. 경력 정보가 아예 없는 공고는 판단할 수 없어 남긴다.
   */
  private static boolean fitsBracket(JobPosting posting, int bracketMinYears) {
    return posting.getExperienceMax() != null
        || posting.getExperienceMin() == null
        || posting.getExperienceMin() >= bracketMinYears - OPEN_ENDED_TOLERANCE_YEARS;
  }

  /** 공개연봉 공고의 서로 다른 회사 수. 회사명이 없으면 같은 회사인지 알 수 없어 공고마다 따로 센다. */
  private static int disclosedCompanyCount(List<JobPosting> postings) {
    Set<String> companies = new HashSet<>();
    for (JobPosting posting : postings) {
      if (posting.disclosedSalary() == null) continue;
      String name = normalizeCompany(posting.getCompanyName());
      companies.add(name.isEmpty() ? "id:" + posting.getId() : name);
    }
    return companies.size();
  }

  /** 법인 표기와 공백만 걷는다 — `㈜인팩` 과 `(주) 인팩` 을 같은 회사로. */
  private static String normalizeCompany(String name) {
    if (name == null) return "";
    return name.replace("㈜", "").replace("(주)", "").replaceAll("\\s+", "");
  }

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

  /**
   * 공고 지역은 「서울 강남구」 같은 한글이라 LIKE 접두사도 한글이어야 한다. 「SEOUL」 같은 영문 코드는 시도로 바꾸고, 바꿀 수 없으면 그대로 둔다(한글 상세
   * 지역은 원문이 더 좁은 조건이다).
   */
  private String postingRegion(String rawRegion) {
    if (!rawRegion.strip().matches("[A-Za-z_]+")) {
      return rawRegion;
    }
    String sido = PeerGroupFallbackResolver.normalizeRegion(rawRegion);
    return PeerGroupFallbackResolver.DIMENSION_ALL.equals(sido) ? rawRegion : sido;
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
}
