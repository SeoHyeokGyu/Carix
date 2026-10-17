package com.carix.peergroup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.SourceMonth;
import com.carix.profile.domain.CareerProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PeerSalaryDistributionResolverTest {

  private JobPostingRepository jobPostingRepository;
  private PeerGroupFallbackResolver peerGroupFallbackResolver;
  private PeerSalaryDistributionResolver resolver;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    peerGroupFallbackResolver =
        new PeerGroupFallbackResolver(mock(com.carix.market.MarketAggregateRepository.class));
    resolver = new PeerSalaryDistributionResolver(jobPostingRepository, peerGroupFallbackResolver);
  }

  @Test
  @DisplayName("resolve - Level 1 완전 일치로 안착 (회사규모 포함)")
  void resolve_level1Match() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE");

    List<JobPosting> postings = createPostings(12, 60000000L);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), eq("LARGE"), eq(4), eq(7)))
        .willReturn(postings);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution).isNotNull();
    assertThat(distribution.fallbackLevel()).isEqualTo(1);
    assertThat(distribution.hasSufficientPeers()).isTrue();
    assertThat(distribution.peerSampleSize()).isEqualTo(12);
    assertThat(distribution.disclosedSalaries()).hasSize(12);
    assertThat(distribution.appliedConditions()).contains("서울 강남구 / 경력 MID / LARGE");
  }

  @Test
  @DisplayName("resolve - companySize가 null이거나 빈칸이면 Level 1을 건너뛰고 Level 2로 안착")
  void resolve_skipLevel1WhenCompanySizeBlank() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "");

    List<JobPosting> postings = createPostings(10, 55000000L);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(postings);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution).isNotNull();
    assertThat(distribution.fallbackLevel()).isEqualTo(2);
    assertThat(distribution.hasSufficientPeers()).isTrue();
    assertThat(distribution.appliedConditions()).contains("회사규모 무관");
  }

  @Test
  @DisplayName("resolve - 프로필 필드가 null인 경우 기본값 및 지역명 접두사(공백 없는 지역) 처리")
  void resolve_defaultProfileValuesAndSingleWordRegion() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch(null, null, "서울특별시", null, null);

    List<JobPosting> postings = createPostings(10, 50000000L);
    // yearsOfExperience == null -> 3 -> JUNIOR -> [0, 3]
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울특별시"), eq("FULL_TIME"), isNull(), eq(0), eq(3)))
        .willReturn(postings);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution).isNotNull();
    assertThat(distribution.fallbackLevel()).isEqualTo(2);
    assertThat(distribution.hasSufficientPeers()).isTrue();
  }

  @Test
  @DisplayName("resolve - 모든 단계 표본 부족 시 Level 5 결과 반환")
  void resolve_insufficientSamplesAcrossAllLevels() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 10, "경기 성남시", "FULL_TIME", null);

    // SENIOR -> [8, 99], adjacent -> MID·SENIOR [4, 99]
    given(
            jobPostingRepository.findPeerGroupCandidates(
                anyString(), anyString(), any(), any(), anyInt(), anyInt()))
        .willReturn(createPostings(3, 80000000L));

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution).isNotNull();
    assertThat(distribution.fallbackLevel()).isEqualTo(5);
    assertThat(distribution.hasSufficientPeers()).isFalse();
    assertThat(distribution.peerSampleSize()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "resolve - 등록월(posted_start_date)별 제시 연봉 추이를 올바르게 집계한다 (표본 부족 시 median=null, 날짜 역전 제외)")
  void resolve_monthlyTrendCalculation() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE");

    List<JobPosting> postings = new ArrayList<>();

    // 2026-05: 12건 (11건 연봉공개 >= 10건, 1건 미공개) -> median 산출, missingRate 8.3%
    for (int i = 0; i < 11; i++) {
      postings.add(
          createPostingWithDate(
              60_000_000L + (i * 1_000_000L),
              java.time.LocalDate.of(2026, 5, 1 + i),
              java.time.LocalDate.of(2026, 5, 20)));
    }
    postings.add(
        createPostingWithDate(
            null, java.time.LocalDate.of(2026, 5, 15), java.time.LocalDate.of(2026, 5, 30)));

    // 2026-06: 6건 (2건 연봉공개 < 10건, 4건 미공개) -> median = null (표본 부족), missingRate 66.7%
    postings.add(
        createPostingWithDate(
            50_000_000L, java.time.LocalDate.of(2026, 6, 2), java.time.LocalDate.of(2026, 6, 20)));
    postings.add(
        createPostingWithDate(
            55_000_000L, java.time.LocalDate.of(2026, 6, 3), java.time.LocalDate.of(2026, 6, 20)));
    for (int i = 0; i < 4; i++) {
      postings.add(
          createPostingWithDate(
              null, java.time.LocalDate.of(2026, 6, 10), java.time.LocalDate.of(2026, 6, 25)));
    }

    // 2026-07: 15건 (14건 연봉공개 >= 10건, 1건 미공개) -> median 산출
    for (int i = 0; i < 14; i++) {
      postings.add(
          createPostingWithDate(
              70_000_000L + (i * 1_000_000L),
              java.time.LocalDate.of(2026, 7, 1 + i),
              java.time.LocalDate.of(2026, 7, 25)));
    }
    postings.add(
        createPostingWithDate(
            null, java.time.LocalDate.of(2026, 7, 15), java.time.LocalDate.of(2026, 7, 30)));

    // 날짜가 null인 공고 & 마감일 역전된 비정상 공고 -> 추이 버킷에서 무시됨
    postings.add(createPostingWithDate(80_000_000L, null, null));
    postings.add(
        createPostingWithDate(
            80_000_000L, java.time.LocalDate.of(2026, 8, 10), java.time.LocalDate.of(2026, 8, 1)));

    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), eq("LARGE"), eq(4), eq(7)))
        .willReturn(postings);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution.trend()).hasSize(3);

    PeerSalaryDistribution.TrendPoint may = distribution.trend().get(0);
    assertThat(may.yearMonth()).isEqualTo("2026-05");
    assertThat(may.sampleSize()).isEqualTo(12);
    assertThat(may.disclosedSampleSize()).isEqualTo(11);
    assertThat(may.missingRate()).isEqualTo(8.3);
    assertThat(may.median()).isNotNull();

    PeerSalaryDistribution.TrendPoint jun = distribution.trend().get(1);
    assertThat(jun.yearMonth()).isEqualTo("2026-06");
    assertThat(jun.sampleSize()).isEqualTo(6);
    assertThat(jun.disclosedSampleSize()).isEqualTo(2);
    assertThat(jun.missingRate()).isEqualTo(66.7);
    assertThat(jun.median()).isNull(); // 표본 부족으로 null 확인

    PeerSalaryDistribution.TrendPoint jul = distribution.trend().get(2);
    assertThat(jul.yearMonth()).isEqualTo("2026-07");
    assertThat(jul.sampleSize()).isEqualTo(15);
    assertThat(jul.disclosedSampleSize()).isEqualTo(14);
    assertThat(jul.missingRate()).isEqualTo(6.7);
    assertThat(jul.median()).isNotNull();
  }

  @Test
  @DisplayName("PeerSalaryDistribution 기본 생성자 - trend가 없으면 빈 리스트로 초기화된다")
  void peerSalaryDistribution_defaultConstructorInitializesEmptyTrend() {
    PeerSalaryDistribution dist = new PeerSalaryDistribution(List.of(50_000_000L), 1, 1, "조건");
    assertThat(dist.trend()).isEmpty();
    assertThat(dist.percentileOf(60_000_000L)).isEqualTo(100);
  }

  @Test
  @DisplayName("resolve - 후보 공고가 전혀 없으면 빈 trend와 fallback 5단계를 반환한다")
  void resolve_emptyPostingsReturnsEmptyTrend() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE");

    given(
            jobPostingRepository.findPeerGroupCandidates(
                any(), any(), any(), any(), anyInt(), anyInt()))
        .willReturn(java.util.Collections.emptyList());

    PeerSalaryDistribution distribution = resolver.resolve(profile);
    assertThat(distribution.trend()).isEmpty();
    assertThat(distribution.peerSampleSize()).isZero();
  }

  @Test
  @DisplayName("resolve - 무작위 표본 월이 있으면 최근·이전 3개월 비교를 싣는다")
  void resolve_attachesTrendComparison() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE");
    List<JobPosting> postings = new ArrayList<>();
    for (int month = 4; month <= 9; month++) {
      for (int i = 0; i < 10; i++) {
        postings.add(sampledPosting("JOBKOREA", "2026%02d".formatted(month), 60_000_000L + i));
      }
    }
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), eq("LARGE"), eq(4), eq(7)))
        .willReturn(postings);
    given(jobPostingRepository.findLatestSampledMonth()).willReturn(Optional.of("202609"));
    given(jobPostingRepository.findSampledSourceMonths("202604", "202609"))
        .willReturn(
            java.util.stream.IntStream.rangeClosed(4, 9)
                .mapToObj(m -> new SourceMonth("JOBKOREA", "2026%02d".formatted(m)))
                .toList());
    resolver.setClock(Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneId.of("Asia/Seoul")));

    TrendComparison comparison = resolver.resolve(profile).trendComparison();

    assertThat(comparison).isNotNull();
    assertThat(comparison.recent().from()).isEqualTo(YearMonth.of(2026, 7));
    assertThat(comparison.previous().from()).isEqualTo(YearMonth.of(2026, 4));
    assertThat(comparison.sources()).containsExactly("JOBKOREA");
    assertThat(comparison.inProgress()).containsExactly(YearMonth.of(2026, 10));
  }

  @Test
  @DisplayName("resolve - 표본 월이 하나도 없으면 비교는 null")
  void resolve_nullComparisonWhenNoBackfill() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE");
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), eq("LARGE"), eq(4), eq(7)))
        .willReturn(createPostings(12, 60_000_000L));
    given(jobPostingRepository.findLatestSampledMonth()).willReturn(Optional.empty());

    assertThat(resolver.resolve(profile).trendComparison()).isNull();
  }

  private JobPosting sampledPosting(String source, String sampledMonth, long salary) {
    return JobPosting.builder()
        .id(UUID.randomUUID())
        .region("서울 강남구")
        .employmentType("FULL_TIME")
        .salaryMin(salary)
        .salaryMax(salary)
        .source(source)
        .sampledMonth(sampledMonth)
        .dedupGroupId(UUID.randomUUID())
        .build();
  }

  private List<JobPosting> createPostings(int count, long salary) {
    List<JobPosting> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      JobPosting posting =
          JobPosting.builder()
              .id(UUID.randomUUID())
              .region("서울 강남구")
              .employmentType("FULL_TIME")
              .salaryMin(salary)
              .salaryMax(salary)
              .dedupGroupId(UUID.randomUUID())
              .postedStartDate(java.time.LocalDate.of(2026, 5, 1))
              .postedEndDate(java.time.LocalDate.of(2026, 5, 31))
              .build();
      list.add(posting);
    }
    return list;
  }

  private JobPosting createPostingWithDate(
      Long salary, java.time.LocalDate start, java.time.LocalDate end) {
    return JobPosting.builder()
        .id(UUID.randomUUID())
        .region("서울 강남구")
        .employmentType("FULL_TIME")
        .salaryMin(salary)
        .salaryMax(salary)
        .dedupGroupId(UUID.randomUUID())
        .postedStartDate(start)
        .postedEndDate(end)
        .build();
  }

  private List<JobPosting> createPostingsWithoutSalary(int count) {
    List<JobPosting> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      list.add(
          JobPosting.builder()
              .id(UUID.randomUUID())
              .region("서울 강남구")
              .employmentType("FULL_TIME")
              .dedupGroupId(UUID.randomUUID())
              .postedStartDate(java.time.LocalDate.of(2026, 5, 1))
              .postedEndDate(java.time.LocalDate.of(2026, 5, 31))
              .build());
    }
    return list;
  }

  private static List<JobPosting> concat(List<JobPosting> a, List<JobPosting> b) {
    List<JobPosting> all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }

  @Test
  @DisplayName("resolve - 공고는 충분해도 공개 연봉이 모자라면 공개 연봉 10건이 되는 단계까지 넓힌다")
  void resolve_settlesAtLevelWithEnoughSalaries() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", null);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(concat(createPostings(4, 60_000_000L), createPostingsWithoutSalary(8)));
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(concat(createPostings(11, 60_000_000L), createPostingsWithoutSalary(29)));

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution.fallbackLevel()).isEqualTo(3);
    assertThat(distribution.disclosedSampleSize()).isEqualTo(11);
    assertThat(distribution.hasSufficientSalaries()).isTrue();
  }

  @Test
  @DisplayName("resolve - 어느 단계도 공개 연봉 10건을 못 채우면 가장 넓은 단계에서 공개 연봉 부족")
  void resolve_widensWhenSalariesShort() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DATA", 5, "서울 강남구", "FULL_TIME", null);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                anyString(), anyString(), any(), any(), anyInt(), anyInt()))
        .willReturn(concat(createPostings(5, 50_000_000L), createPostingsWithoutSalary(195)));

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution.fallbackLevel()).isEqualTo(5);
    assertThat(distribution.hasSufficientPeers()).isTrue();
    assertThat(distribution.hasSufficientSalaries()).isFalse();
    assertThat(distribution.disclosedSampleSize()).isEqualTo(5);
  }

  @Test
  @DisplayName("resolvePostings - 스킬 추천 사다리는 공개 연봉을 보지 않고 공고 10건에서 멈춘다")
  void resolvePostings_ignoresSalaries() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", null);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(concat(createPostings(4, 60_000_000L), createPostingsWithoutSalary(8)));
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(concat(createPostings(11, 60_000_000L), createPostingsWithoutSalary(29)));

    assertThat(resolver.resolvePostings(profile).level()).isEqualTo(2);
  }
}
