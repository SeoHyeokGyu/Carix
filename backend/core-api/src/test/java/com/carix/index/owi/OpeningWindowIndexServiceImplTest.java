package com.carix.index.owi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.index.dto.OwiResult;
import com.carix.index.enums.IndexStatus;
import com.carix.index.mvi.PeerGroupFallbackResolver;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.profile.domain.CareerProfile;
import com.carix.profile.repository.CareerProfileRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpeningWindowIndexServiceImplTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String BASE_YM = "202608";
  private static final LocalDate MONTH_START = LocalDate.of(2026, 8, 1);
  private static final LocalDate MONTH_END = LocalDate.of(2026, 8, 31);

  private CareerProfileRepository careerProfileRepository;
  private JobPostingRepository jobPostingRepository;
  private PeerGroupFallbackResolver peerGroupFallbackResolver;
  private OpeningWindowIndexServiceImpl service;

  @BeforeEach
  void setUp() {
    careerProfileRepository = mock(CareerProfileRepository.class);
    jobPostingRepository = mock(JobPostingRepository.class);
    peerGroupFallbackResolver =
        new PeerGroupFallbackResolver(mock(com.carix.market.MarketAggregateRepository.class));

    service =
        new OpeningWindowIndexServiceImpl(
            careerProfileRepository, jobPostingRepository, peerGroupFallbackResolver);
  }

  private CareerProfile createProfile() {
    return CareerProfile.builder()
        .userId(USER_ID)
        .jobFamily("DEVELOPER")
        .yearsOfExperience(5) // MID
        .region("서울 강남구")
        .employmentType("FULL_TIME")
        .companySize("LARGE")
        .build();
  }

  private JobPosting createPosting(
      UUID dedupGroupId, LocalDate start, LocalDate end, String jobFamily, String region) {
    return JobPosting.builder()
        .id(UUID.randomUUID())
        .dedupGroupId(dedupGroupId)
        .jobFamily(jobFamily)
        .region(region)
        .employmentType("FULL_TIME")
        .companySize("LARGE")
        .experienceMin(3)
        .experienceMax(7)
        .postedStartDate(start)
        .postedEndDate(end)
        .build();
  }

  @Test
  @DisplayName("프로필이 없으면 NOT_AVAILABLE 을 반환한다")
  void 프로필없음_NOT_AVAILABLE() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(result.value()).isNull();
  }

  @Test
  @DisplayName("마감일 보유 표본이 10건 미만이면 INSUFFICIENT_SAMPLE 과 상시채용 비율을 반환한다")
  void 마감일_표본부족_INSUFFICIENT_SAMPLE() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    // 시장 전체 공고
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(
            List.of(
                createPosting(null, MONTH_START, MONTH_START.plusDays(30), "DEVELOPER", "서울 강남구")));

    // 동료집단 공고: 마감일 있는 공고 5개, 상시채용 5개 (총 10개이지만 deadline = 5 < 10)
    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울 강남구"));
    }
    for (int i = 0; i < 5; i++) {
      peerPostings.add(createPosting(null, MONTH_START, null, "DEVELOPER", "서울 강남구"));
    }

    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.value()).isNull();
    assertThat(result.medianWindowDays()).isNull();
    assertThat(result.peerSampleSize()).isEqualTo(10);
    assertThat(result.deadlineSampleSize()).isEqualTo(5);
    assertThat(result.openEndedRatePercent()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("마감일 표본이 충분하면 OWI 를 정상 산출하고 이상치 클램프 [25, 400] 범위 내에 둔다")
  void OWI_정상_산출() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    // 시장 전체 공고 (전체시장 중앙값 = 20일)
    List<JobPosting> marketPostings = new ArrayList<>();
    for (int i = 0; i < 15; i++) {
      marketPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울 강남구"));
    }
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    // 동료집단 공고: 마감일 있는 공고 10개 (개방일수 30일), 상시채용 2개 -> 중앙값 30일
    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(30), "DEVELOPER", "서울 강남구"));
    }
    for (int i = 0; i < 2; i++) {
      peerPostings.add(createPosting(null, MONTH_START, null, "DEVELOPER", "서울 강남구"));
    }

    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    // OWI = (동료집단 중앙 30 / 전체시장 중앙 20) * 100 = 150.00
    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.value()).isEqualByComparingTo("150.00");
    assertThat(result.medianWindowDays()).isEqualTo(30);
    assertThat(result.marketMedianWindowDays()).isEqualTo(20);
    assertThat(result.peerSampleSize()).isEqualTo(12);
    assertThat(result.deadlineSampleSize()).isEqualTo(10);
    // 상시채용 비율 = (2 / 12) * 100 = 16.67%
    assertThat(result.openEndedRatePercent()).isEqualByComparingTo("16.67");
    assertThat(result.fallbackLevel()).isEqualTo(1);
    assertThat(result.appliedConditions()).isEqualTo("서울 강남구 / 경력 MID / LARGE");
  }

  @Test
  @DisplayName("역전 공고(end < start)는 표본에서 제외된다")
  void 역전_공고_제외() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    // 전체시장
    List<JobPosting> marketPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      marketPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(10), "DEVELOPER", "서울"));
    }
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    // 동료집단: 정상 마감 9개 + 역전 2개 (start > end) -> 유효 deadline = 9 (< 10)
    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 9; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(15), "DEVELOPER", "서울 강남구"));
    }
    // 역전 공고
    peerPostings.add(
        createPosting(null, MONTH_START.plusDays(10), MONTH_START, "DEVELOPER", "서울 강남구"));
    peerPostings.add(
        createPosting(null, MONTH_START.plusDays(20), MONTH_START, "DEVELOPER", "서울 강남구"));

    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    // 역전 공고 2개 제외되어 유효 표본은 9개뿐 -> INSUFFICIENT_SAMPLE
    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.deadlineSampleSize()).isEqualTo(9);
    assertThat(result.peerSampleSize()).isEqualTo(9);
  }

  @Test
  @DisplayName("중복 공고(dedup_group_id 동일)는 대표 1건만 반영된다")
  void 중복_공고_제거() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    // 전체시장
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(
            List.of(createPosting(null, MONTH_START, MONTH_START.plusDays(10), "DEVELOPER", "서울")));

    UUID dedupGroup = UUID.randomUUID();
    List<JobPosting> peerPostings = new ArrayList<>();
    // 같은 dedupGroupId를 가진 공고 5개 -> 1개로 집계됨
    for (int i = 0; i < 5; i++) {
      peerPostings.add(
          createPosting(dedupGroup, MONTH_START, MONTH_START.plusDays(15), "DEVELOPER", "서울 강남구"));
    }

    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    // 중복 제거 후 1건만 남음
    assertThat(result.deadlineSampleSize()).isEqualTo(1);
    assertThat(result.peerSampleSize()).isEqualTo(1);
    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
  }

  @Test
  @DisplayName("OWI 가 400을 초과하면 400.00으로 클램프되고 25 미만이면 25.00으로 클램프된다")
  void 이상치_클램프() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    // 전체시장 중앙값 = 10일
    List<JobPosting> marketPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      marketPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(10), "DEVELOPER", "서울"));
    }
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    // 동료집단 중앙값 = 50일 -> (50 / 10) * 100 = 500 -> 클램프 400.00
    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(50), "DEVELOPER", "서울 강남구"));
    }

    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.value()).isEqualByComparingTo("400.00");
  }

  @Test
  @DisplayName("OWI 가 25 미만이면 25.00으로 하한 클램프된다")
  void 이상치_하한_클램프() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    // 전체시장 중앙값 = 100일
    List<JobPosting> marketPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      marketPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(100), "DEVELOPER", "서울"));
    }
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    // 동료집단 중앙값 = 5일 -> (5 / 100) * 100 = 5 -> 클램프 25.00
    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(5), "DEVELOPER", "서울 강남구"));
    }

    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.value()).isEqualByComparingTo("25.00");
  }

  @Test
  @DisplayName("baseYearMonth 가 null 이거나 비어있거나 잘못된 형식이면 NOT_AVAILABLE 을 반환한다")
  void baseYearMonth_비정상_NOT_AVAILABLE() {
    assertThat(service.calculate(USER_ID, null).status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(service.calculate(USER_ID, "").status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(service.calculate(USER_ID, "   ").status()).isEqualTo(IndexStatus.NOT_AVAILABLE);
    assertThat(service.calculate(USER_ID, "invalid-date").status())
        .isEqualTo(IndexStatus.NOT_AVAILABLE);
  }

  @Test
  @DisplayName("전체시장 기준 모수가 결측이면 INSUFFICIENT_SAMPLE 을 반환한다")
  void 전체시장_모수_부재_INSUFFICIENT_SAMPLE() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    // 전체시장에 공고 없음
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(List.of());

    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울 강남구"));
    }
    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.value()).isNull();
    assertThat(result.marketMedianWindowDays()).isNull();
  }

  @Test
  @DisplayName("전체시장 공고 중 역전 공고는 전체시장 중앙값 산출에서 제외된다")
  void 전체시장_역전_공고_제외() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    List<JobPosting> marketPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      marketPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울"));
    }
    // 전체시장에 역전 공고 추가
    marketPostings.add(
        createPosting(null, MONTH_START.plusDays(10), MONTH_START, "DEVELOPER", "서울"));

    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울 강남구"));
    }
    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.marketMedianWindowDays()).isEqualTo(20);
  }

  @Test
  @DisplayName("회사규모가 없고 경력이 주니어/시니어인 경우에도 완화 사다리가 정상 동작한다")
  void 회사규모_없음_및_주니어_시니어_완화() {
    CareerProfile juniorProfile =
        CareerProfile.builder()
            .userId(USER_ID)
            .jobFamily("DEVELOPER")
            .yearsOfExperience(null) // null -> default 3 (JUNIOR)
            .region("서울")
            .employmentType(null)
            .companySize(null) // Level 1 스킵 -> Level 2 시작
            .build();

    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(juniorProfile));

    List<JobPosting> marketPostings =
        List.of(createPosting(null, MONTH_START, MONTH_START.plusDays(30), "DEVELOPER", "서울"));
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(30), "DEVELOPER", "서울"));
    }
    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.fallbackLevel()).isEqualTo(2);
    assertThat(result.appliedConditions()).contains("회사규모 무관");
  }

  @Test
  @DisplayName("전체시장 중앙 개방일수가 0일 이하이면 INSUFFICIENT_SAMPLE 을 반환한다")
  void 전체시장_중앙값_0일_이하_INSUFFICIENT_SAMPLE() {
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(createProfile()));

    // 시작일과 종료일이 같은 당일 마감 공고 (windowDays = 0)
    List<JobPosting> marketPostings =
        List.of(createPosting(null, MONTH_START, MONTH_START, "DEVELOPER", "서울"));
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(10), "DEVELOPER", "서울 강남구"));
    }
    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.INSUFFICIENT_SAMPLE);
    assertThat(result.marketMedianWindowDays()).isEqualTo(0);
  }

  @Test
  @DisplayName("프로필의 companySize 나 region 이 공백 문자열인 경우 fallback 으로 치환된다")
  void 공백문자열_필드_폴백() {
    CareerProfile blankProfile =
        CareerProfile.builder()
            .userId(USER_ID)
            .jobFamily("   ") // isBlank() -> "DEVELOPER"
            .yearsOfExperience(10) // SENIOR
            .region("   ") // isBlank() -> "서울 강남구"
            .employmentType("   ") // isBlank() -> "FULL_TIME"
            .companySize("   ") // isBlank() -> "회사규모 무관", Level 1 스킵
            .build();

    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(blankProfile));

    // 전체시장에 상시채용 공고(days == null)와 마감 공고 혼합 -> days == null 분기 커버
    List<JobPosting> marketPostings =
        List.of(
            createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울"),
            createPosting(null, MONTH_START, null, "DEVELOPER", "서울"));
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    // 동료집단에 시작일 없는 공고(days == null && !isOpenEnded) 혼합 -> !isOpenEnded 분기 커버
    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울 강남구"));
    }
    peerPostings.add(createPosting(null, null, MONTH_START.plusDays(20), "DEVELOPER", "서울 강남구"));

    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    OwiResult result = service.calculate(USER_ID, BASE_YM);

    assertThat(result.status()).isEqualTo(IndexStatus.OK);
    assertThat(result.appliedConditions()).contains("회사규모 무관");
  }

  @Test
  @DisplayName("전체시장 중앙값 캐싱: 동일 월 재계산 시 findAllByRegistrationMonth 를 중복 호출하지 않는다")
  void marketMedianWindowDays_caching() {
    CareerProfile profile = createProfile();
    given(careerProfileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));

    List<JobPosting> marketPostings =
        List.of(createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울"));
    given(jobPostingRepository.findAllByRegistrationMonth(MONTH_START, MONTH_END))
        .willReturn(marketPostings);

    List<JobPosting> peerPostings = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      peerPostings.add(
          createPosting(null, MONTH_START, MONTH_START.plusDays(20), "DEVELOPER", "서울 강남구"));
    }
    given(
            jobPostingRepository.findPeerGroupCandidatesByRegistrationMonth(
                anyString(),
                any(),
                any(),
                any(),
                anyInt(),
                anyInt(),
                eq(MONTH_START),
                eq(MONTH_END)))
        .willReturn(peerPostings);

    // 첫 번째 호출 -> DB 조회
    service.calculate(USER_ID, BASE_YM);
    // 두 번째 호출 -> 캐시 사용
    service.calculate(USER_ID, BASE_YM);

    // 전체 시장 조회가 단 1회만 일어났는지 검증
    org.mockito.Mockito.verify(jobPostingRepository, org.mockito.Mockito.times(1))
        .findAllByRegistrationMonth(MONTH_START, MONTH_END);

    // 캐시 삭제 후 다시 호출 -> 다시 DB 조회 1회 추가 발생 (총 2회)
    service.clearMarketMedianCache();
    service.calculate(USER_ID, BASE_YM);
    org.mockito.Mockito.verify(jobPostingRepository, org.mockito.Mockito.times(2))
        .findAllByRegistrationMonth(MONTH_START, MONTH_END);
  }
}
