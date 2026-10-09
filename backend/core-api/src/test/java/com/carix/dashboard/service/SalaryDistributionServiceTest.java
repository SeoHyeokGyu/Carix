package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.dashboard.dto.SalaryDistributionResponse;
import com.carix.index.CurrentSalaryProvider;
import com.carix.index.dto.CurrentSalary;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.index.mvi.WageBaselineResolver;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.market.MarketAggregateRepository;
import com.carix.peergroup.PeerSalaryDistributionResolver;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import com.carix.wagestat.WageStatObservation;
import com.carix.wagestat.WageStatObservationRepository;
import com.carix.wagestat.WageStatSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 동료집단 연봉 분포 산출 검증 (API 규격 §5.2).
 *
 * <p>규격은 {@code market_aggregate} 의 원천별 5수치가 아니라 <b>원 공고({@code job_posting}) 직접 집계</b>를 요구한다 — 결합
 * 분포와 백분위는 원천별 요약에서 재구성할 수 없기 때문이다. 유효 공개연봉 정의와 {@code dedup_group_id} 대표 선택은 collector 의 {@code
 * MarketAggregateService} 와 같은 규약을 쓴다.
 */
class SalaryDistributionServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String BASE_YM = "202607";

  private CareerProfileRepository careerProfileRepository;
  private JobPostingRepository jobPostingRepository;
  private WageBaselineResolver wageBaselineResolver;
  private CurrentSalaryProvider currentSalaryProvider;
  private WageStatObservationRepository wageStatObservationRepository;
  private SalaryDistributionService service;

  @BeforeEach
  void setUp() {
    careerProfileRepository = mock(CareerProfileRepository.class);
    jobPostingRepository = mock(JobPostingRepository.class);
    wageBaselineResolver = mock(WageBaselineResolver.class);
    currentSalaryProvider = mock(CurrentSalaryProvider.class);
    wageStatObservationRepository = mock(WageStatObservationRepository.class);

    // 완화 사다리와 중복 제거는 PeerSalaryDistributionResolver 로 옮겼다. 시나리오 §6.1 도 같은 분포를
    // 읽기 때문이다. 여기서는 진짜 구현을 끼워 이 테스트가 검증하던 경로를 그대로 태운다.
    service =
        new SalaryDistributionService(
            careerProfileRepository,
            wageBaselineResolver,
            currentSalaryProvider,
            new PeerSalaryDistributionResolver(
                jobPostingRepository,
                new PeerGroupFallbackResolver(mock(MarketAggregateRepository.class))),
            wageStatObservationRepository);

    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile()));
    given(wageBaselineResolver.resolveBaseline(any(CareerProfile.class)))
        .willReturn(new com.carix.index.dto.WageBaseline(72_000_000L, "초과급여 제외, 상여 포함", "202606"));
    given(currentSalaryProvider.currentGrossSalary(eq(USER_ID), anyString()))
        .willReturn(Optional.empty());
  }

  @Test
  @DisplayName("표본이 10건 미만이면 200 으로 INSUFFICIENT_SAMPLE 과 표본 수를 돌려준다")
  void 표본부족() {
    givenCandidates(postings(4, 50_000_000L));

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(response.sampleSize()).isEqualTo(4);
    assertThat(response.minRequired()).isEqualTo(10);
    assertThat(response.postingReference()).isNull();
  }

  @Test
  @DisplayName("경력 사다리는 다섯 구간의 중앙값·상승률·출처와 사용자 구간을 제공한다")
  void 경력사다리_구간과_기준선출처() {
    givenCandidates(postings(4, 50_000_000L));
    given(wageBaselineResolver.resolveBaseline(any(CareerProfile.class)))
        .willReturn(
            new com.carix.index.dto.WageBaseline(
                72_000_000L, "ANNUAL_GROSS_MEDIAN", "202606", WageStatSource.FALLBACK));
    givenWageObservation("ENTRY", 30_000_000L, WageStatSource.FALLBACK);
    givenWageObservation("JUNIOR", 40_000_000L, WageStatSource.KOSIS);
    givenWageObservation("MID", 50_000_000L, WageStatSource.KOSIS);
    givenWageObservation("SENIOR", 60_000_000L, WageStatSource.UNKNOWN);
    given(wageStatObservationRepository.findLatestBaseline("DEVELOPER", "LEAD", "ALL"))
        .willReturn(Optional.empty());
    given(wageStatObservationRepository.findLatestBaseline("DEVELOPER", "LEAD", "전국"))
        .willReturn(Optional.of(wageObservation("LEAD", 80_000_000L, WageStatSource.KOSIS)));

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(response.baseline().basis()).isEqualTo("AGE_BAND_PROXY");
    assertThat(response.baseline().source()).isEqualTo("FALLBACK");
    assertThat(response.careerLadder().userBracket()).isEqualTo("MID");
    assertThat(response.careerLadder().steps())
        .extracting("bracket")
        .containsExactly("ENTRY", "JUNIOR", "MID", "SENIOR", "LEAD");
    assertThat(response.careerLadder().steps().get(0).raiseFromPrevious()).isNull();
    assertThat(response.careerLadder().steps().get(1).raiseFromPrevious()).isEqualTo(33.33);
    assertThat(response.careerLadder().steps().get(2).raiseFromPrevious()).isEqualTo(25.0);
    assertThat(response.careerLadder().steps().get(3).source()).isEqualTo("UNKNOWN");
    assertThat(response.careerLadder().steps().get(4).median()).isEqualTo(80_000_000L);
    assertThat(response.careerLadder().steps().get(4).raiseFromPrevious()).isEqualTo(33.33);
  }

  @Test
  @DisplayName("기준선과 사다리 관측치가 없으면 출처 UNKNOWN과 빈 중앙값을 돌려준다")
  void 경력사다리_관측치없음() {
    givenCandidates(postings(4, 50_000_000L));

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.careerLadder().steps()).hasSize(5);
    assertThat(response.careerLadder().steps())
        .allSatisfy(
            step -> {
              assertThat(step.median()).isNull();
              assertThat(step.raiseFromPrevious()).isNull();
              assertThat(step.source()).isEqualTo("UNKNOWN");
            });
  }

  @Test
  @DisplayName("연도 축(b14) — 특정 연도(year)를 지정하면 해당 연도의 경력 사다리와 사용 가능 연도 목록을 반환한다")
  void 경력사다리_연도지정_조회() {
    givenCandidates(postings(4, 50_000_000L));
    given(wageStatObservationRepository.findDistinctBasePeriodsByJobFamily("DEVELOPER"))
        .willReturn(List.of("2021", "2022", "2023", "2024", "2025"));

    given(wageStatObservationRepository.findBaselineByYear("DEVELOPER", "ENTRY", "ALL", "2024"))
        .willReturn(
            Optional.of(
                wageObservationWithPeriod("ENTRY", 32_000_000L, WageStatSource.KOSIS, "2024")));
    given(wageStatObservationRepository.findBaselineByYear("DEVELOPER", "JUNIOR", "ALL", "2024"))
        .willReturn(
            Optional.of(
                wageObservationWithPeriod("JUNIOR", 42_000_000L, WageStatSource.KOSIS, "2024")));
    given(wageStatObservationRepository.findBaselineByYear("DEVELOPER", "MID", "ALL", "2024"))
        .willReturn(
            Optional.of(
                wageObservationWithPeriod("MID", 52_000_000L, WageStatSource.KOSIS, "2024")));
    given(wageStatObservationRepository.findBaselineByYear("DEVELOPER", "SENIOR", "ALL", "2024"))
        .willReturn(
            Optional.of(
                wageObservationWithPeriod("SENIOR", 62_000_000L, WageStatSource.KOSIS, "2024")));
    given(wageStatObservationRepository.findBaselineByYear("DEVELOPER", "LEAD", "ALL", "2024"))
        .willReturn(
            Optional.of(
                wageObservationWithPeriod("LEAD", 72_000_000L, WageStatSource.KOSIS, "2024")));

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM, "2024");

    assertThat(response.careerLadder()).isNotNull();
    assertThat(response.careerLadder().basePeriod()).isEqualTo("2024");
    assertThat(response.careerLadder().availableYears())
        .containsExactly("2021", "2022", "2023", "2024", "2025");
    assertThat(response.careerLadder().steps()).hasSize(5);
    assertThat(response.careerLadder().steps().get(0).median()).isEqualTo(32_000_000L);
    assertThat(response.careerLadder().steps().get(1).median()).isEqualTo(42_000_000L);
    assertThat(response.careerLadder().steps().get(1).raiseFromPrevious()).isEqualTo(31.25);

    SalaryDistributionResponse.CareerLadder legacy =
        new SalaryDistributionResponse.CareerLadder("MID", List.of());
    assertThat(legacy.basePeriod()).isNull();
    assertThat(legacy.availableYears()).isEmpty();
  }

  @Test
  @DisplayName("표본이 충분하면 유효 공개연봉의 5수치를 계산한다")
  void 정상_5수치() {
    List<JobPosting> candidates = new ArrayList<>();
    for (long salary : new long[] {30, 40, 50, 60, 70, 80, 90, 100, 110, 120}) {
      candidates.add(posting(UUID.randomUUID(), null, salary * 1_000_000L, salary * 1_000_000L));
    }
    givenCandidates(candidates);

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.status()).isEqualTo("OK");
    assertThat(response.baseline().basis()).isEqualTo("AGE_BAND_PROXY");
    assertThat(response.baseline().source()).isEqualTo("UNKNOWN");
    assertThat(response.careerLadder().userBracket()).isEqualTo("MID");
    assertThat(response.careerLadder().steps()).hasSize(5);
    assertThat(response.postingReference().min()).isEqualTo(30_000_000L);
    assertThat(response.postingReference().max()).isEqualTo(120_000_000L);
    assertThat(response.postingReference().median()).isEqualTo(80_000_000L);
    assertThat(response.postingReference().sampleSize()).isEqualTo(10);
    assertThat(response.postingReference().missingRate()).isZero();
    assertThat(response.trend()).isNotEmpty();
    assertThat(response.trend().get(0).yearMonth()).isEqualTo("2026-05");
    assertThat(response.trend().get(0).median()).isEqualTo(80_000_000L);
  }

  @Test
  @DisplayName("정상_5수치 응답에 등록월별 제시 연봉 추이(trend)가 포함된다")
  void 정상_추이_포함() {
    List<JobPosting> candidates = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      candidates.add(
          JobPosting.builder()
              .id(UUID.randomUUID())
              .salaryMin(60_000_000L)
              .salaryMax(60_000_000L)
              .jobFamily("DEVELOPER")
              .region("서울 강남구")
              .experienceMin(3)
              .experienceMax(7)
              .companySize("MEDIUM")
              .employmentType("FULL_TIME")
              .postedStartDate(java.time.LocalDate.of(2026, 5, 10 + i))
              .postedEndDate(java.time.LocalDate.of(2026, 5, 30))
              .build());
    }
    givenCandidates(candidates);

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.trend()).isNotEmpty();
    assertThat(response.trend().get(0).yearMonth()).isEqualTo("2026-05");
    assertThat(response.trend().get(0).median()).isEqualTo(60_000_000L);
    assertThat(response.trend().get(0).sampleSize()).isEqualTo(10);
    assertThat(response.trend().get(0).disclosedSampleSize()).isEqualTo(10);
    assertThat(response.trend().get(0).missingRate()).isZero();
  }

  @Test
  @DisplayName("공고의 등록일(postedStartDate)이 없어서 trend가 비어있으면 빈 trend 리스트를 반환한다")
  void 등록일_부재시_빈_추이_반환() {
    List<JobPosting> candidates = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      candidates.add(
          JobPosting.builder()
              .id(UUID.randomUUID())
              .salaryMin(60_000_000L)
              .salaryMax(60_000_000L)
              .jobFamily("DEVELOPER")
              .region("서울 강남구")
              .experienceMin(3)
              .experienceMax(7)
              .companySize("MEDIUM")
              .employmentType("FULL_TIME")
              .build());
    }
    givenCandidates(candidates);

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.status()).isEqualTo("OK");
    assertThat(response.trend()).isEmpty();
  }

  @Test
  @DisplayName("공개연봉이 전건 결측이면 표본 부족이고 공개 연봉 0건·missingRate 100% 만 싣는다")
  void 전건_연봉_미공개() {
    List<JobPosting> candidates = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      candidates.add(posting(UUID.randomUUID(), null, null, null));
    }
    givenCandidates(candidates);

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(response.postingReference().sampleSize()).isZero();
    assertThat(response.postingReference().missingRate()).isEqualTo(100.0);
    assertThat(response.postingReference().median()).isNull();
  }

  @Test
  @DisplayName("온보딩 미완료(커리어 프로필 없음)면 sampleSize 0이고 peerGroup과 baseline이 null이다")
  void 프로필_미존재_온보딩미완료() {
    UUID unknownUser = UUID.randomUUID();
    given(careerProfileRepository.findByUserId(unknownUser)).willReturn(Optional.empty());

    SalaryDistributionResponse response = service.resolve(unknownUser, BASE_YM);

    assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(response.sampleSize()).isZero();
    assertThat(response.peerGroup()).isNull();
    assertThat(response.baseline()).isNull();
    assertThat(response.trend()).isEmpty();
  }

  @Test
  @DisplayName("같은 dedup_group_id 공고는 한 건으로 센다")
  void 중복제거() {
    UUID group = UUID.randomUUID();
    List<JobPosting> candidates = new ArrayList<>(postings(10, 60_000_000L));
    // 같은 그룹의 원천 중복 5건을 더해도 표본 수는 늘지 않는다
    for (int i = 0; i < 5; i++) {
      candidates.add(posting(UUID.randomUUID(), group, 200_000_000L, 200_000_000L));
    }
    givenCandidates(candidates);

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.sampleSize()).isEqualTo(11);
    assertThat(response.postingReference().max()).isEqualTo(200_000_000L);
  }

  @Test
  @DisplayName("연봉 미공개 공고는 5수치에서 빠지고 missingRate 에 반영된다")
  void 공개연봉_결측() {
    List<JobPosting> candidates = new ArrayList<>(postings(12, 60_000_000L));
    candidates.add(posting(UUID.randomUUID(), null, null, null));
    candidates.add(posting(UUID.randomUUID(), null, null, null));
    candidates.add(posting(UUID.randomUUID(), null, null, null));
    givenCandidates(candidates);

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.status()).isEqualTo("OK");
    assertThat(response.postingReference().sampleSize()).isEqualTo(12);
    assertThat(response.postingReference().missingRate()).isEqualTo(20.0);
  }

  @Test
  @DisplayName("동료집단 표본은 충분한데 공개연봉이 1건뿐이면 표본 부족이고 5수치를 내지 않는다")
  void 공개연봉_1건() {
    List<JobPosting> candidates = new ArrayList<>();
    candidates.add(posting(UUID.randomUUID(), null, 60_000_000L, 60_000_000L));
    for (int i = 0; i < 9; i++) {
      candidates.add(posting(UUID.randomUUID(), null, null, null));
    }
    givenCandidates(candidates);

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    // 5수치·백분위는 공개연봉 위에서 매기므로 공개연봉도 최소 표본(10)을 채워야 한다.
    assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(response.sampleSize()).isEqualTo(10);
    assertThat(response.postingReference().sampleSize()).isEqualTo(1);
    assertThat(response.postingReference().missingRate()).isEqualTo(90.0);
    assertThat(response.postingReference().min()).isNull();
  }

  @Test
  @DisplayName("동료 공고는 충분해도 공개 연봉이 10건 미만이면 표본 부족이고 공개 연봉 수·결측률만 낸다")
  void 공개연봉_10건미만_표본부족() {
    List<JobPosting> candidates = new ArrayList<>(postings(5, 50_000_000L));
    for (int i = 0; i < 195; i++) {
      candidates.add(posting(UUID.randomUUID(), null, null, null));
    }
    givenCandidates(candidates);

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(response.sampleSize()).isEqualTo(200);
    assertThat(response.minRequired()).isEqualTo(10);
    assertThat(response.userPosition()).isNull();
    assertThat(response.postingReference().sampleSize()).isEqualTo(5);
    assertThat(response.postingReference().median()).isNull();
    assertThat(response.postingReference().missingRate()).isEqualTo(97.5);
  }

  @Test
  @DisplayName("연봉 이력이 없으면 userPosition 을 채우지 않는다 — 임의 값으로 메우지 않는다")
  void 연봉이력_없음() {
    givenCandidates(postings(10, 60_000_000L));

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.userPosition()).isNull();
  }

  @Test
  @DisplayName("연봉 이력이 있으면 분포 내 백분위를 계산한다")
  void 연봉이력_있음() {
    List<JobPosting> candidates = new ArrayList<>();
    for (long salary : new long[] {30, 40, 50, 60, 70, 80, 90, 100, 110, 120}) {
      candidates.add(posting(UUID.randomUUID(), null, salary * 1_000_000L, salary * 1_000_000L));
    }
    givenCandidates(candidates);
    given(currentSalaryProvider.currentGrossSalary(eq(USER_ID), anyString()))
        .willReturn(
            Optional.of(new CurrentSalary(75_000_000L, java.time.LocalDate.of(2026, 1, 1))));

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.userPosition().grossSalary()).isEqualTo(75_000_000L);
    assertThat(response.userPosition().percentile()).isEqualTo(50);
  }

  @Test
  @DisplayName("프로필이 없으면 표본 부족으로 돌려주고 예외를 던지지 않는다")
  void 프로필_없음() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

    SalaryDistributionResponse response = service.resolve(USER_ID, BASE_YM);

    assertThat(response.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    assertThat(response.sampleSize()).isZero();
  }

  // --- fixtures ---

  private void givenCandidates(List<JobPosting> candidates) {
    given(
            jobPostingRepository.findPeerGroupCandidates(
                anyString(), anyString(), any(), any(), anyInt(), anyInt()))
        .willReturn(candidates);
  }

  private List<JobPosting> postings(int count, long salary) {
    List<JobPosting> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      list.add(posting(UUID.randomUUID(), null, salary, salary));
    }
    return list;
  }

  private JobPosting posting(UUID id, UUID dedupGroupId, Long salaryMin, Long salaryMax) {
    return JobPosting.builder()
        .id(id)
        .dedupGroupId(dedupGroupId)
        .salaryMin(salaryMin)
        .salaryMax(salaryMax)
        .jobFamily("DEVELOPER")
        .region("서울 강남구")
        .experienceMin(3)
        .experienceMax(7)
        .companySize("MEDIUM")
        .employmentType("FULL_TIME")
        .postedStartDate(java.time.LocalDate.of(2026, 5, 1))
        .build();
  }

  private CareerProfile profile() {
    return CareerProfile.builder()
        .userId(USER_ID)
        .jobFamily("DEVELOPER")
        .yearsOfExperience(5)
        .region("서울 강남구")
        .employmentType("FULL_TIME")
        .companySize("MEDIUM")
        .build();
  }

  private void givenWageObservation(String bracket, long median, WageStatSource source) {
    given(wageStatObservationRepository.findLatestBaseline("DEVELOPER", bracket, "ALL"))
        .willReturn(Optional.of(wageObservation(bracket, median, source)));
  }

  private WageStatObservation wageObservation(String bracket, long median, WageStatSource source) {
    return wageObservationWithPeriod(bracket, median, source, "202500");
  }

  private WageStatObservation wageObservationWithPeriod(
      String bracket, long median, WageStatSource source, String basePeriod) {
    return WageStatObservation.builder()
        .jobFamily("DEVELOPER")
        .experienceBracket(bracket)
        .region("ALL")
        .wageDefinition("ANNUAL_GROSS_MEDIAN")
        .wageMedian(median)
        .basePeriod(basePeriod)
        .statVintage("202600")
        .source(source)
        .build();
  }
}
