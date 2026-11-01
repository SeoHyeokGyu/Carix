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
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE", null);

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
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "", null);

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
  @DisplayName("resolve - 모든 단계 표본 부족 시 Level 5 결과 반환")
  void resolve_insufficientSamplesAcrossAllLevels() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 10, "경기 성남시", "FULL_TIME", null, null);

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
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE", null);

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
  @DisplayName("resolve - 무작위 표본 월이 있으면 최근·이전 3개월 비교를 싣는다")
  void resolve_attachesTrendComparison() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE", null);
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
    given(jobPostingRepository.findLatestSampledMonth("202610")).willReturn(Optional.of("202609"));
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
  @DisplayName("resolve - 형식이 깨진 표본 월은 건너뛰고 500 을 내지 않는다")
  void resolve_ignoresMalformedSourceMonth() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "LARGE", null);
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
    given(jobPostingRepository.findLatestSampledMonth("202610")).willReturn(Optional.of("202609"));
    List<SourceMonth> sourceMonths =
        new ArrayList<>(
            java.util.stream.IntStream.rangeClosed(4, 9)
                .mapToObj(m -> new SourceMonth("JOBKOREA", "2026%02d".formatted(m)))
                .toList());
    sourceMonths.add(new SourceMonth("JOBKOREA", "202613"));
    given(jobPostingRepository.findSampledSourceMonths("202604", "202609"))
        .willReturn(sourceMonths);
    resolver.setClock(Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneId.of("Asia/Seoul")));

    TrendComparison comparison = resolver.resolve(profile).trendComparison();

    assertThat(comparison).isNotNull();
    assertThat(comparison.sources()).containsExactly("JOBKOREA");
  }

  // --- 중복 묶음 대표 (데이터 아키텍처 §1.4) ---

  private JobPosting grouped(UUID group, UUID id, Long salary, java.time.LocalDate posted) {
    return JobPosting.builder()
        .id(id)
        .region("서울 강남구")
        .employmentType("FULL_TIME")
        .salaryMin(salary)
        .salaryMax(salary)
        .dedupGroupId(group)
        .postedStartDate(posted)
        .build();
  }

  private PeerSalaryDistribution resolveLevel2(List<JobPosting> candidates) {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", "", null);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(candidates);
    return resolver.resolve(profile);
  }

  @Test
  @DisplayName("중복 묶음 — 연봉을 공개한 공고가 대표다. 비공개 공고가 먼저 와도 그 묶음의 연봉이 표본에 든다")
  void dedupe_공개연봉_공고가_대표() {
    UUID group = UUID.randomUUID();
    List<JobPosting> candidates = new ArrayList<>(createPostings(10, 60_000_000L));
    candidates.add(0, grouped(group, UUID.randomUUID(), null, java.time.LocalDate.of(2026, 4, 1)));
    candidates.add(
        grouped(group, UUID.randomUUID(), 50_000_000L, java.time.LocalDate.of(2026, 5, 1)));

    PeerSalaryDistribution distribution = resolveLevel2(candidates);

    assertThat(distribution.peerSampleSize()).isEqualTo(11);
    assertThat(distribution.disclosedSalaries()).hasSize(11).contains(50_000_000L);
  }

  @Test
  @DisplayName("중복 묶음 — 공개 공고가 여럿이면 게시일이 이른 것, 같으면 id 순. 조회 순서가 바뀌어도 같은 대표다")
  void dedupe_조회순서와_무관() {
    UUID group = UUID.randomUUID();
    UUID smallId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID largeId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    List<JobPosting> base = createPostings(10, 60_000_000L);
    JobPosting later =
        grouped(group, UUID.randomUUID(), 40_000_000L, java.time.LocalDate.of(2026, 5, 1));
    JobPosting earlyLargeId =
        grouped(group, largeId, 80_000_000L, java.time.LocalDate.of(2026, 4, 1));
    JobPosting earlySmallId =
        grouped(group, smallId, 70_000_000L, java.time.LocalDate.of(2026, 4, 1));

    List<JobPosting> forward = new ArrayList<>(base);
    forward.addAll(List.of(later, earlyLargeId, earlySmallId));
    List<Long> first = resolveLevel2(forward).disclosedSalaries();

    List<JobPosting> backward = new ArrayList<>(forward);
    java.util.Collections.reverse(backward);
    List<Long> second = resolveLevel2(backward).disclosedSalaries();

    assertThat(first).contains(70_000_000L).doesNotContain(40_000_000L, 80_000_000L);
    assertThat(second).isEqualTo(first);
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
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", null, null);
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
    profile.patch("DATA", 5, "서울 강남구", "FULL_TIME", null, null);
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
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", null, null);
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

  // 2026-11-01 운영: 개발·서울·MID·대기업 1단계가 공개 연봉 11건으로 안착했는데 그중 5건이 같은 회사의
  // 재등록 공고였다. 소수 회사가 분포·협상 목표·오퍼 수준을 모두 정했다.
  @Test
  @DisplayName("resolve - 공개 연봉이 10건이어도 서로 다른 회사가 10곳 미만이면 다음 단계로 넓힌다")
  void resolve_widensWhenDisclosedCompaniesAreFew() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", null, null);
    List<JobPosting> sameCompany = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      sameCompany.add(posting("에스텍시스템", 31_000_000L, null, null));
    }
    List<JobPosting> manyCompanies = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      manyCompanies.add(posting("회사" + i, 60_000_000L, null, null));
    }
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(sameCompany);
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(manyCompanies);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution.fallbackLevel()).isEqualTo(3);
    assertThat(distribution.hasSufficientSalaries()).isTrue();
  }

  @Test
  @DisplayName("resolve - 끝까지 서로 다른 회사가 10곳 미만이면 공개 연봉 부족이다 — 협상 목표·오퍼 수준도 이 판정을 쓴다")
  void resolve_insufficientWhenCompaniesShortEverywhere() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", null, null);
    List<JobPosting> fewCompanies = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      fewCompanies.add(posting("회사" + (i % 3), 50_000_000L, null, null));
    }
    given(
            jobPostingRepository.findPeerGroupCandidates(
                anyString(), anyString(), any(), any(), anyInt(), anyInt()))
        .willReturn(fewCompanies);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution.disclosedSampleSize()).isEqualTo(20);
    assertThat(distribution.hasSufficientSalaries()).isFalse();
  }

  // 같은 운영 사례에서 공개 11건 중 10건이 "경력 0년~"(최대 경력 없음) 신입 공고였다. 겹침 조건만 보면
  // 최대 경력이 없는 공고는 모든 구간에 걸려 MID 분포를 신입 연봉으로 끌어내렸다.
  @Test
  @DisplayName("resolve - 최대 경력이 없는 공고는 최소 경력이 구간 하한보다 1년 넘게 낮으면 넣지 않는다 — 「3년~」은 MID 에 든다")
  void resolve_excludesOpenEndedPostingsBelowBracket() {
    CareerProfile profile = new CareerProfile(UUID.randomUUID());
    profile.patch("DEVELOPER", 5, "서울 강남구", "FULL_TIME", null, null);
    List<JobPosting> candidates = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      candidates.add(posting("신입" + i, 30_000_000L, 0, null));
      candidates.add(posting("저연차" + i, 40_000_000L, 2, null));
      candidates.add(posting("경력" + i, 60_000_000L, 3, null));
      candidates.add(posting("범위" + i, 65_000_000L, 3, 8));
    }
    given(
            jobPostingRepository.findPeerGroupCandidates(
                eq("DEVELOPER"), eq("서울 강남구"), eq("FULL_TIME"), isNull(), eq(4), eq(7)))
        .willReturn(candidates);

    PeerSalaryDistribution distribution = resolver.resolve(profile);

    assertThat(distribution.fallbackLevel()).isEqualTo(2);
    assertThat(distribution.peerSampleSize()).isEqualTo(24);
    assertThat(distribution.disclosedSalaries())
        .contains(60_000_000L)
        .doesNotContain(30_000_000L, 40_000_000L);
  }

  private JobPosting posting(
      String companyName, long salary, Integer experienceMin, Integer experienceMax) {
    return JobPosting.builder()
        .id(UUID.randomUUID())
        .companyName(companyName)
        .region("서울 강남구")
        .employmentType("FULL_TIME")
        .experienceMin(experienceMin)
        .experienceMax(experienceMax)
        .salaryMin(salary)
        .salaryMax(salary)
        .dedupGroupId(UUID.randomUUID())
        .postedStartDate(java.time.LocalDate.of(2026, 5, 1))
        .build();
  }
}
