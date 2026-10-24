package com.carix.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.pipeline.dto.MarketAggregateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class MarketAggregateServiceTest {

  private JobPostingRepository jobPostingRepository;
  private MarketAggregateRepository marketAggregateRepository;
  private MarketAggregateService marketAggregateService;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    marketAggregateRepository = mock(MarketAggregateRepository.class);
    marketAggregateService =
        new MarketAggregateService(jobPostingRepository, marketAggregateRepository);
  }

  private JobPosting grouped(UUID group, String externalId, Long salary) {
    return JobPosting.builder()
        .source("JOBKOREA")
        .externalId(externalId)
        .jobFamily("BACKEND")
        .experienceMin(3)
        .salaryMin(salary)
        .salaryMax(salary)
        .dedupGroupId(group)
        .postedStartDate(java.time.LocalDate.of(2026, 8, 1))
        .firstSeenMonth("202608")
        .lastSeenMonth("202608")
        .collectedAt(Instant.now())
        .build();
  }

  private MarketAggregate allBackendJunior(List<JobPosting> postings) {
    org.mockito.Mockito.reset(marketAggregateRepository);
    when(jobPostingRepository.findAll()).thenReturn(postings);
    marketAggregateService.aggregateMarketDataForMonth("202608");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MarketAggregate>> captor = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository).saveAll(captor.capture());
    return captor.getValue().stream()
        .filter(
            m ->
                "ALL".equals(m.getSource())
                    && "BACKEND".equals(m.getJobFamily())
                    && "JUNIOR".equals(m.getExperienceBracket())
                    && m.getSkillId() == null)
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("중복 묶음 대표는 연봉 공개 공고다 — 조회 순서가 바뀌어도 같은 대표 (core-api 와 같은 규약, 데이터 아키텍처 §1.4)")
  void dedup_대표는_공개연봉_공고_순서무관() {
    UUID group = UUID.randomUUID();
    JobPosting undisclosed = grouped(group, "jk-hidden", null);
    JobPosting disclosed = grouped(group, "jk-open", 50_000_000L);

    MarketAggregate forward = allBackendJunior(List.of(undisclosed, disclosed));
    MarketAggregate backward = allBackendJunior(List.of(disclosed, undisclosed));

    assertThat(forward.getPostingCount()).isEqualTo(1);
    assertThat(forward.getSalaryMissingRatePercent()).isEqualByComparingTo("0.00");
    assertThat(backward.getSalaryMissingRatePercent()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("aggregateMarketDataForMonth - 직군·경력별 공고수, 스킬 수요율, 중위연봉을 정확하게 계산하여 저장한다")
  void aggregateMarketData_calculatesCorrectly() {
    // Given
    Skill javaSkill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").category("BACKEND").build();

    JobPosting p1 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .experienceMin(3)
            .salaryMin(40_000_000L)
            .salaryMax(60_000_000L)
            .firstSeenMonth("202608")
            .lastSeenMonth("202608")
            .collectedAt(Instant.now())
            .build();
    p1.addSkill(javaSkill);

    JobPosting p2 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-2")
            .jobFamily("BACKEND")
            .experienceMin(2)
            .salaryMin(50_000_000L)
            .salaryMax(70_000_000L)
            .firstSeenMonth("202608")
            .lastSeenMonth("202608")
            .collectedAt(Instant.now())
            .build();
    p2.addSkill(javaSkill);

    when(jobPostingRepository.findAll()).thenReturn(List.of(p1, p2));

    // When
    int count = marketAggregateService.aggregateMarketDataForMonth("202608");

    // Then
    assertThat(count).isGreaterThan(0);
    verify(marketAggregateRepository).deleteByBaseYearMonth("202608");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MarketAggregate>> captor = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository).saveAll(captor.capture());

    List<MarketAggregate> saved = captor.getValue();
    assertThat(saved).isNotEmpty();

    // BACKEND & JUNIOR 슬라이스 검증
    MarketAggregate backendJunior =
        saved.stream()
            .filter(
                m ->
                    "ALL".equals(m.getSource())
                        && "BACKEND".equals(m.getJobFamily())
                        && "JUNIOR".equals(m.getExperienceBracket())
                        && m.getSkillId() == null)
            .findFirst()
            .orElseThrow();

    assertThat(backendJunior.getPostingCount()).isEqualTo(2);
    assertThat(backendJunior.getSalaryMissingRatePercent()).isEqualByComparingTo("0.00");
  }

  @Test
  @SuppressWarnings("unchecked")
  @DisplayName("공개연봉이 부분 결측인 경우 결측률이 올바르게 계산된다")
  void aggregateMarketData_partialSalaryMissing() {
    JobPosting p1 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .experienceMin(1)
            .salaryMin(50_000_000L)
            .salaryMax(60_000_000L)
            .firstSeenMonth("202601")
            .lastSeenMonth("202601")
            .collectedAt(Instant.now())
            .build();
    JobPosting p2 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-2")
            .jobFamily("BACKEND")
            .experienceMin(2)
            .firstSeenMonth("202601")
            .lastSeenMonth("202601")
            .collectedAt(Instant.now())
            .build();
    when(jobPostingRepository.findAll()).thenReturn(List.of(p1, p2));

    marketAggregateService.aggregateMarketDataForMonth("202601");

    ArgumentCaptor<List<MarketAggregate>> captor = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository, atLeastOnce()).saveAll(captor.capture());
    List<MarketAggregate> saved = captor.getValue();

    MarketAggregate backendJunior =
        saved.stream()
            .filter(
                m ->
                    "ALL".equals(m.getSource())
                        && "BACKEND".equals(m.getJobFamily())
                        && "JUNIOR".equals(m.getExperienceBracket())
                        && m.getSkillId() == null)
            .findFirst()
            .orElseThrow();

    assertThat(backendJunior.getPostingCount()).isEqualTo(2);
    assertThat(backendJunior.getSalaryMissingRatePercent()).isEqualByComparingTo("50.00");
  }

  @Test
  @DisplayName("기준년월 유효성 검사 - null 또는 비정상 형식 시 IllegalArgumentException 발생")
  void normalizeMonth_exceptions() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> marketAggregateService.aggregateMarketDataForMonth(null))
        .isInstanceOf(IllegalArgumentException.class);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> marketAggregateService.aggregateMarketDataForMonth("2026-8"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("b18 - 활동월(firstSeenMonth ~ lastSeenMonth) 기준 필터링 검증")
  void b18_filterActivePostings_onlyIncludesPostingsWithinActivityWindow() {
    JobPosting p1 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .firstSeenMonth("202501")
            .lastSeenMonth("202503")
            .build();

    JobPosting p2 =
        JobPosting.builder()
            .source("SARAMIN")
            .externalId("sr-1")
            .jobFamily("BACKEND")
            .firstSeenMonth("202504")
            .lastSeenMonth("202506")
            .build();

    List<JobPosting> all = List.of(p1, p2);

    // 202501: p1만 포함
    List<JobPosting> active202501 =
        marketAggregateService.filterActivePostingsForMonth(all, "202501");
    assertThat(active202501).containsExactly(p1);

    // 202502: p1만 포함 (중간월)
    List<JobPosting> active202502 =
        marketAggregateService.filterActivePostingsForMonth(all, "202502");
    assertThat(active202502).containsExactly(p1);

    // 202504: p2만 포함
    List<JobPosting> active202504 =
        marketAggregateService.filterActivePostingsForMonth(all, "202504");
    assertThat(active202504).containsExactly(p2);

    // 202507: 둘 다 미포함 (활동 구간 밖)
    List<JobPosting> active202507 =
        marketAggregateService.filterActivePostingsForMonth(all, "202507");
    assertThat(active202507).isEmpty();
  }

  @Test
  @DisplayName("b18 - 해당 월에 활동 중인 공고가 없으면 기존 데이터 삭제 후 0건 반환")
  void b18_aggregateMarketDataForMonth_emptyWhenNoActivePostings() {
    JobPosting p =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .firstSeenMonth("202501")
            .lastSeenMonth("202501")
            .build();
    when(jobPostingRepository.findAll()).thenReturn(List.of(p));

    int count = marketAggregateService.aggregateMarketDataForMonth("202503");

    assertThat(count).isEqualTo(0);
    verify(marketAggregateRepository).deleteByBaseYearMonth("202503");
  }

  @Test
  @DisplayName("b18 - isPostingActiveInMonth 날짜 폴백(postedStartDate, collectedAt) 및 역전 보정 검증")
  void b18_isPostingActiveInMonth_fallbacksAndClamping() {
    // 1. null 파라미터
    assertThat(marketAggregateService.isPostingActiveInMonth(null, "202608")).isFalse();
    assertThat(marketAggregateService.isPostingActiveInMonth(JobPosting.builder().build(), null))
        .isFalse();

    // 2. 날짜 정보가 전혀 없는 경우
    assertThat(
            marketAggregateService.isPostingActiveInMonth(JobPosting.builder().build(), "202608"))
        .isFalse();

    // 3. 하이픈이 포함된 firstSeenMonth("2025-01"), lastSeenMonth("2025-03")
    JobPosting hyphenPosting =
        JobPosting.builder().firstSeenMonth("2025-01").lastSeenMonth("2025-03").build();
    assertThat(marketAggregateService.isPostingActiveInMonth(hyphenPosting, "202502")).isTrue();
    assertThat(marketAggregateService.isPostingActiveInMonth(hyphenPosting, "202504")).isFalse();

    // 4. postedStartDate / postedEndDate 폴백
    JobPosting datePosting =
        JobPosting.builder()
            .postedStartDate(java.time.LocalDate.of(2025, 4, 1))
            .postedEndDate(java.time.LocalDate.of(2025, 6, 30))
            .build();
    assertThat(marketAggregateService.isPostingActiveInMonth(datePosting, "202504")).isTrue();
    assertThat(marketAggregateService.isPostingActiveInMonth(datePosting, "202505")).isTrue();
    assertThat(marketAggregateService.isPostingActiveInMonth(datePosting, "202506")).isTrue();
    assertThat(marketAggregateService.isPostingActiveInMonth(datePosting, "202507")).isFalse();

    // 5. collectedAt 폴백 (단일월)
    JobPosting collectedPosting =
        JobPosting.builder().collectedAt(Instant.parse("2026-08-15T00:00:00Z")).build();
    assertThat(marketAggregateService.isPostingActiveInMonth(collectedPosting, "202608")).isTrue();
    assertThat(marketAggregateService.isPostingActiveInMonth(collectedPosting, "202607")).isFalse();

    // 6. 시작월 > 종료월 역전 데이터 시 시작월 단일월로 클램핑
    JobPosting reversedPosting =
        JobPosting.builder().firstSeenMonth("202505").lastSeenMonth("202501").build();
    assertThat(marketAggregateService.isPostingActiveInMonth(reversedPosting, "202505")).isTrue();
    assertThat(marketAggregateService.isPostingActiveInMonth(reversedPosting, "202501")).isFalse();
  }

  @Test
  @DisplayName("b18 - 월별 활동 공고에 따라 서로 다른 스킬 수요율 및 모수가 정상 분리 집계된다 (MVI 100.0 고정 해소)")
  void b18_differentMonths_produceDistinctAggregates() {
    Skill javaSkill =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").category("BACKEND").build();
    Skill pythonSkill =
        Skill.builder().id(UUID.randomUUID()).standardName("Python").category("BACKEND").build();

    // 202501월에만 활동하는 Java 공고
    JobPosting p1 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .experienceMin(1)
            .salaryMin(40_000_000L)
            .salaryMax(50_000_000L)
            .firstSeenMonth("202501")
            .lastSeenMonth("202501")
            .collectedAt(Instant.now())
            .build();
    p1.addSkill(javaSkill);

    // 202502월에만 활동하는 Python 공고
    JobPosting p2 =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-2")
            .jobFamily("BACKEND")
            .experienceMin(1)
            .salaryMin(60_000_000L)
            .salaryMax(70_000_000L)
            .firstSeenMonth("202502")
            .lastSeenMonth("202502")
            .collectedAt(Instant.now())
            .build();
    p2.addSkill(pythonSkill);

    when(jobPostingRepository.findAll()).thenReturn(List.of(p1, p2));

    // 202501월 집계 실행
    int countJan = marketAggregateService.aggregateMarketDataForMonth("202501");
    assertThat(countJan).isGreaterThan(0);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MarketAggregate>> captorJan = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository, atLeastOnce()).saveAll(captorJan.capture());
    List<MarketAggregate> savedJan = captorJan.getValue();

    // 202501월에는 Java 스킬만 존재하고 Python 스킬은 없어야 함
    boolean hasJavaJan = savedJan.stream().anyMatch(m -> javaSkill.getId().equals(m.getSkillId()));
    boolean hasPythonJan =
        savedJan.stream().anyMatch(m -> pythonSkill.getId().equals(m.getSkillId()));
    assertThat(hasJavaJan).isTrue();
    assertThat(hasPythonJan).isFalse();

    // 202502월 집계 실행
    int countFeb = marketAggregateService.aggregateMarketDataForMonth("202502");
    assertThat(countFeb).isGreaterThan(0);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MarketAggregate>> captorFeb = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository, atLeastOnce()).saveAll(captorFeb.capture());
    List<MarketAggregate> savedFeb = captorFeb.getValue();

    // 202502월에는 Python 스킬만 존재하고 Java 스킬은 없어야 함
    boolean hasJavaFeb = savedFeb.stream().anyMatch(m -> javaSkill.getId().equals(m.getSkillId()));
    boolean hasPythonFeb =
        savedFeb.stream().anyMatch(m -> pythonSkill.getId().equals(m.getSkillId()));
    assertThat(hasJavaFeb).isFalse();
    assertThat(hasPythonFeb).isTrue();
  }

  @Test
  @DisplayName("extractActiveMonths - 36개월 초과 장기 구간, 역전 구간, 비정상 월 문자열, 날짜 결측 처리 분기 검증")
  void extractActiveMonths_branchCoverage() {
    // 1. 36개월 초과 장기 공고: 202001 ~ 202501 -> 시작월과 종료월만 추가
    JobPosting longPosting =
        JobPosting.builder()
            .source("JOBKOREA")
            .firstSeenMonth("202001")
            .lastSeenMonth("202501")
            .build();

    // 2. 시작월 > 종료월 역전 공고
    JobPosting reversedPosting =
        JobPosting.builder()
            .source("JOBKOREA")
            .firstSeenMonth("202508")
            .lastSeenMonth("202503")
            .build();

    // 3. 비정상 형식 문자열 보유 공고 (postedStartDate 로 폴백)
    JobPosting malformedMonthPosting =
        JobPosting.builder()
            .source("JOBKOREA")
            .firstSeenMonth("invalid")
            .lastSeenMonth("bad")
            .postedStartDate(java.time.LocalDate.of(2025, 9, 1))
            .postedEndDate(java.time.LocalDate.of(2025, 9, 30))
            .build();

    // 4. 날짜 정보가 전혀 없는 공고
    JobPosting noDatePosting = JobPosting.builder().source("JOBKOREA").build();

    when(jobPostingRepository.findAll())
        .thenReturn(List.of(longPosting, reversedPosting, malformedMonthPosting, noDatePosting));

    int count = marketAggregateService.aggregateAllMarketData();
    assertThat(count).isGreaterThanOrEqualTo(0);

    // 5. 날짜 정보 없는 공고만 존재할 때: 현재월 fallback
    when(jobPostingRepository.findAll()).thenReturn(List.of(noDatePosting));
    marketAggregateService.aggregateAllMarketData();
  }

  @Test
  @DisplayName(
      "buildAggregatesForSource - dedupGroupId null, jobFamily null, 부분 연봉 결측, 다양한 경력 브래킷 분기 검증")
  void buildAggregatesForSource_branchCoverage() {
    Skill java =
        Skill.builder().id(UUID.randomUUID()).standardName("Java").category("BACKEND").build();

    // dedupGroupId 가 null 인 공고 (id 를 키로 대체), jobFamily null, salaryMax 만 존재
    JobPosting pDedupNull =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .dedupGroupId(null)
            .jobFamily(null)
            .experienceMin(0) // ENTRY
            .salaryMin(null)
            .salaryMax(50_000_000L)
            .firstSeenMonth("202608")
            .lastSeenMonth("202608")
            .build();
    pDedupNull.addSkill(java);

    // salaryMin 만 존재 (hasSalary = false), JUNIOR (경력 2년), 스킬 없음
    JobPosting pNoSkill =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-2")
            .jobFamily("BACKEND")
            .experienceMin(2) // JUNIOR
            .salaryMin(40_000_000L)
            .salaryMax(null)
            .firstSeenMonth("202608")
            .lastSeenMonth("202608")
            .build();

    // MID (경력 5년), SENIOR (경력 10년), LEAD (경력 15년)
    JobPosting pMid =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-3")
            .jobFamily("BACKEND")
            .experienceMin(5)
            .salaryMin(50_000_000L)
            .salaryMax(70_000_000L)
            .firstSeenMonth("202608")
            .lastSeenMonth("202608")
            .build();
    pMid.addSkill(java);

    JobPosting pSenior =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-4")
            .jobFamily("BACKEND")
            .experienceMin(10)
            .firstSeenMonth("202608")
            .lastSeenMonth("202608")
            .build();

    JobPosting pLead =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-5")
            .jobFamily("BACKEND")
            .experienceMin(15)
            .firstSeenMonth("202608")
            .lastSeenMonth("202608")
            .build();

    when(jobPostingRepository.findAll())
        .thenReturn(List.of(pDedupNull, pNoSkill, pMid, pSenior, pLead));

    int count = marketAggregateService.aggregateMarketDataForMonth("202608");
    assertThat(count).isGreaterThan(0);
  }

  /** 잡코리아 공고 하나. 표본이면 {@code sampledMonth} 를 단다. */
  private static JobPosting jobkorea(
      String externalId, String company, String seenMonth, String sampledMonth, Skill skill) {
    return posting("JOBKOREA", externalId, company, seenMonth, sampledMonth, skill);
  }

  private static JobPosting posting(
      String source,
      String externalId,
      String company,
      String seenMonth,
      String sampledMonth,
      Skill skill) {
    JobPosting p =
        JobPosting.builder()
            .source(source)
            .externalId(externalId)
            .companyName(company)
            .jobFamily("DEVELOPER")
            .experienceMin(5)
            .firstSeenMonth(seenMonth)
            .lastSeenMonth(seenMonth)
            .sampledMonth(sampledMonth)
            .collectedAt(Instant.now())
            .build();
    if (skill != null) {
      p.addSkill(skill);
    }
    return p;
  }

  private List<MarketAggregate> aggregate(String month, List<JobPosting> postings) {
    when(jobPostingRepository.findAll()).thenReturn(postings);
    marketAggregateService.aggregateMarketDataForMonth(month);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MarketAggregate>> captor = ArgumentCaptor.forClass(List.class);
    verify(marketAggregateRepository).saveAll(captor.capture());
    return captor.getValue();
  }

  private static MarketAggregate total(List<MarketAggregate> saved, String source) {
    return saved.stream()
        .filter(
            m ->
                source.equals(m.getSource())
                    && "DEVELOPER".equals(m.getJobFamily())
                    && "MID".equals(m.getExperienceBracket())
                    && "ALL".equals(m.getRegion())
                    && "ALL".equals(m.getEmploymentType())
                    && "ALL".equals(m.getCompanySize())
                    && m.getSkillId() == null)
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("잡코리아 원천 집계는 그 달 무작위 표본만 센다 — 정기 수집분은 통합(ALL) 집계에만 들어간다")
  void jobkoreaSourceCountsOnlySampledPostingsOfMonth() {
    Skill java = Skill.builder().id(UUID.randomUUID()).standardName("Java").build();
    List<JobPosting> postings =
        List.of(
            jobkorea("1", "가", "202608", "202608", java),
            jobkorea("2", "나", "202608", "202608", null),
            jobkorea("3", "다", "202608", null, java), // 정기 수집만
            jobkorea("4", "라", "202609", "202608", java)); // 다음 달에 처음 본 공고를 표본이 202608 로 뽑음

    List<MarketAggregate> saved = aggregate("202608", postings);

    assertThat(total(saved, "JOBKOREA").getPostingCount()).isEqualTo(3);
    assertThat(total(saved, "ALL").getPostingCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("잡코리아 표본은 같은 회사를 월 3건까지만 센다 — 반복 등록이 수요율을 끌고 가지 않게")
  void jobkoreaSampleCapsPostingsPerCompanyPerMonth() {
    List<JobPosting> postings =
        List.of(
            jobkorea("10", "㈜조인아이티", "202608", "202608", null),
            jobkorea("11", "조인아이티", "202608", "202608", null),
            jobkorea("12", "(주)조인아이티", "202608", "202608", null),
            jobkorea("13", "조인아이티", "202608", "202608", null),
            jobkorea("14", "카릭스", "202608", "202608", null));

    List<MarketAggregate> saved = aggregate("202608", postings);

    assertThat(total(saved, "JOBKOREA").getPostingCount()).isEqualTo(4);
  }

  @Test
  @DisplayName("b25: 다차원(지역·회사규모·고용형태) 슬라이스가 올바르게 롤업되어 모수 행 및 시도별 스킬 행이 생성된다")
  void b25_multiDimensionalAggregationGeneratesExpectedSlices() {
    Skill python = Skill.builder().id(UUID.randomUUID()).standardName("Python").build();
    JobPosting p =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-multi-1")
            .jobFamily("DEVELOPER")
            .experienceMin(5) // MID
            .region("서울 강남구")
            .employmentType("FULL_TIME")
            .companySize("LARGE")
            .salaryMin(60_000_000L)
            .salaryMax(80_000_000L)
            .sampledMonth("202608")
            .firstSeenMonth("202608")
            .lastSeenMonth("202608")
            .collectedAt(Instant.now())
            .build();
    p.addSkill(python);

    List<MarketAggregate> saved = aggregate("202608", List.of(p));

    // 1) 5차원 완전 일치 모수 행 (DEVELOPER / MID / 서울 / FULL_TIME / LARGE)
    MarketAggregate fullDimensionPeer =
        saved.stream()
            .filter(
                m ->
                    "JOBKOREA".equals(m.getSource())
                        && "DEVELOPER".equals(m.getJobFamily())
                        && "MID".equals(m.getExperienceBracket())
                        && "서울".equals(m.getRegion())
                        && "FULL_TIME".equals(m.getEmploymentType())
                        && "LARGE".equals(m.getCompanySize())
                        && m.getSkillId() == null)
            .findFirst()
            .orElseThrow();
    assertThat(fullDimensionPeer.getPostingCount()).isEqualTo(1);
    assertThat(fullDimensionPeer.getSalaryMissingRatePercent()).isEqualByComparingTo("0.00");

    // 2) 회사규모 완화 모수 행 (companySize = 'ALL')
    boolean hasRelaxedCompanySize =
        saved.stream()
            .anyMatch(
                m ->
                    "JOBKOREA".equals(m.getSource())
                        && "DEVELOPER".equals(m.getJobFamily())
                        && "MID".equals(m.getExperienceBracket())
                        && "서울".equals(m.getRegion())
                        && "FULL_TIME".equals(m.getEmploymentType())
                        && "ALL".equals(m.getCompanySize())
                        && m.getSkillId() == null);
    assertThat(hasRelaxedCompanySize).isTrue();

    // 3) 전국 완화 기본 모수 행 (region = 'ALL', companySize = 'ALL', employmentType = 'ALL')
    boolean hasNationwideBase =
        saved.stream()
            .anyMatch(
                m ->
                    "JOBKOREA".equals(m.getSource())
                        && "DEVELOPER".equals(m.getJobFamily())
                        && "MID".equals(m.getExperienceBracket())
                        && "ALL".equals(m.getRegion())
                        && "ALL".equals(m.getEmploymentType())
                        && "ALL".equals(m.getCompanySize())
                        && m.getSkillId() == null);
    assertThat(hasNationwideBase).isTrue();

    // 4) 시도별 스킬 행 생성 (region = '서울', bracket = 'ALL', skill = python)
    boolean hasRegionalSkillRow =
        saved.stream()
            .anyMatch(
                m ->
                    "JOBKOREA".equals(m.getSource())
                        && "DEVELOPER".equals(m.getJobFamily())
                        && "ALL".equals(m.getExperienceBracket())
                        && "서울".equals(m.getRegion())
                        && "ALL".equals(m.getEmploymentType())
                        && "ALL".equals(m.getCompanySize())
                        && python.getId().equals(m.getSkillId()));
    assertThat(hasRegionalSkillRow).isTrue();
  }

  @Test
  @DisplayName("aggregateMarketData 요청 DTO 분기 검증 (null, month, year, startYear-endYear, all)")
  void aggregateMarketData_requestBranches() {
    JobPosting p = grouped(UUID.randomUUID(), "100", 50_000_000L);
    when(jobPostingRepository.findAll()).thenReturn(List.of(p));

    // 1) request == null -> aggregateAllMarketData
    int c1 = marketAggregateService.aggregateMarketData(null);
    assertThat(c1).isGreaterThanOrEqualTo(0);

    // 2) month 지정
    int c2 =
        marketAggregateService.aggregateMarketData(
            new MarketAggregateRequest("202608", null, null, null, null));
    assertThat(c2).isGreaterThanOrEqualTo(0);

    // 3) year 지정
    int c3 =
        marketAggregateService.aggregateMarketData(
            new MarketAggregateRequest(null, "2026", null, null, null));
    assertThat(c3).isGreaterThanOrEqualTo(0);

    // 4) startYear, endYear 지정
    int c4 =
        marketAggregateService.aggregateMarketData(
            new MarketAggregateRequest(null, null, "2026", "2026", null));
    assertThat(c4).isGreaterThanOrEqualTo(0);

    // 5) startYear 지정, endYear null
    int c5 =
        marketAggregateService.aggregateMarketData(
            new MarketAggregateRequest(null, null, "2026", null, null));
    assertThat(c5).isGreaterThanOrEqualTo(0);

    // 6) all == true
    int c6 =
        marketAggregateService.aggregateMarketData(
            new MarketAggregateRequest(null, null, null, null, true));
    assertThat(c6).isGreaterThanOrEqualTo(0);

    // 7) 빈 request -> fallback aggregateAllMarketData
    int c7 =
        marketAggregateService.aggregateMarketData(
            new MarketAggregateRequest(null, null, null, null, false));
    assertThat(c7).isGreaterThanOrEqualTo(0);
  }

  @Test
  @DisplayName("공고가 비어있을 때의 집계 분기 검증 (모든 집계 진입점에서 0 반환)")
  void aggregate_withEmptyPostings_returnsZero() {
    when(jobPostingRepository.findAll()).thenReturn(List.of());

    assertThat(marketAggregateService.aggregateAllMarketData()).isEqualTo(0);
    assertThat(marketAggregateService.aggregateMarketDataForMonth("202608")).isEqualTo(0);
    assertThat(marketAggregateService.aggregateMarketDataForMonths(List.of())).isEqualTo(0);
    assertThat(marketAggregateService.aggregateMarketDataForMonths(List.of("202608"))).isEqualTo(0);
  }

  @Test
  @DisplayName("filterActivePostingsForMonth null 및 빈 목록 분기 검증")
  void filterActivePostingsForMonth_branches() {
    assertThat(marketAggregateService.filterActivePostingsForMonth(null, "202608")).isEmpty();
    assertThat(marketAggregateService.filterActivePostingsForMonth(List.of(), "202608")).isEmpty();
    assertThat(
            marketAggregateService.filterActivePostingsForMonth(
                List.of(grouped(UUID.randomUUID(), "1", null)), null))
        .isEmpty();
  }

  @Test
  @DisplayName("isPostingActiveInMonth 분기 검증 (null, startMonth null, startMonth > endMonth 등)")
  void isPostingActiveInMonth_branches() {
    assertThat(marketAggregateService.isPostingActiveInMonth(null, "202608")).isFalse();
    assertThat(
            marketAggregateService.isPostingActiveInMonth(
                grouped(UUID.randomUUID(), "1", null), null))
        .isFalse();

    // startMonth 가 null 인 공고 (firstSeenMonth, postedStartDate, sampledMonth, collectedAt 모두 없음)
    JobPosting noDatePosting = JobPosting.builder().externalId("nodate").build();
    assertThat(marketAggregateService.isPostingActiveInMonth(noDatePosting, "202608")).isFalse();

    // startMonth > endMonth 인 역전된 공고
    JobPosting inversedPosting =
        JobPosting.builder()
            .externalId("inv")
            .firstSeenMonth("202609")
            .lastSeenMonth("202607")
            .build();
    // startMonth 가 endMonth 보다 크면 endMonth = startMonth 로 교정됨
    assertThat(marketAggregateService.isPostingActiveInMonth(inversedPosting, "202609")).isTrue();
    assertThat(marketAggregateService.isPostingActiveInMonth(inversedPosting, "202608")).isFalse();
  }

  @Test
  @DisplayName("extractActiveMonths 다양한 날짜 조건 및 분기 검증")
  void extractActiveMonths_branches() {
    // 1) sampledMonth 포함 + 정상 36개월 이내 구간
    JobPosting p1 =
        JobPosting.builder()
            .externalId("p1")
            .sampledMonth("202608")
            .firstSeenMonth("202608")
            .lastSeenMonth("202609")
            .build();

    // 2) 36개월 초과 장기 구간
    JobPosting p2 =
        JobPosting.builder()
            .externalId("p2")
            .firstSeenMonth("202001")
            .lastSeenMonth("202501")
            .build();

    // 3) 파싱 예외 발생하는 잘못된 날짜 문자열 (6자리 숫자이나 월 범위 13 초과)
    JobPosting p3 = JobPosting.builder().externalId("p3").firstSeenMonth("202613").build();

    Set<String> months =
        ReflectionTestUtils.invokeMethod(
            marketAggregateService, "extractActiveMonths", List.of(p1, p2, p3));
    assertThat(months).contains("202608", "202609", "202001", "202501", "202613");

    // 4) 빈 공고 목록 -> 현재 연월 폴백
    Set<String> emptyMonths =
        ReflectionTestUtils.invokeMethod(marketAggregateService, "extractActiveMonths", List.of());
    assertThat(emptyMonths).isNotEmpty();
  }

  @Test
  @DisplayName("determineBracket 및 normalizeDimension 분기 검증")
  void dimensionHelperBranches() {
    // determineBracket: null, 0, 2, 5, 10, 15
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                marketAggregateService, "determineBracket", (Integer) null))
        .isEqualTo("ENTRY");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(marketAggregateService, "determineBracket", 0))
        .isEqualTo("ENTRY");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(marketAggregateService, "determineBracket", 2))
        .isEqualTo("JUNIOR");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(marketAggregateService, "determineBracket", 5))
        .isEqualTo("MID");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                marketAggregateService, "determineBracket", 10))
        .isEqualTo("SENIOR");
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                marketAggregateService, "determineBracket", 15))
        .isEqualTo("LEAD");

    // normalizeDimension: null, blank, UNKNOWN, ALL, normal
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                marketAggregateService, "normalizeDimension", (String) null))
        .isNull();
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                marketAggregateService, "normalizeDimension", "   "))
        .isNull();
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                marketAggregateService, "normalizeDimension", "UNKNOWN"))
        .isNull();
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                marketAggregateService, "normalizeDimension", "ALL"))
        .isNull();
    assertThat(
            ReflectionTestUtils.<String>invokeMethod(
                marketAggregateService, "normalizeDimension", " FULL_TIME "))
        .isEqualTo("FULL_TIME");
  }

  @Test
  @DisplayName("calculateSalaryMissingRate 및 countDisclosedPostings 분기 검증")
  void salaryMetricsBranches() {
    // calculateSalaryMissingRate: total=0, disclosed=0, total=disclosed, partial
    assertThat(
            ReflectionTestUtils.<BigDecimal>invokeMethod(
                marketAggregateService, "calculateSalaryMissingRate", 0, 0))
        .isEqualByComparingTo(BigDecimal.valueOf(100.00));
    assertThat(
            ReflectionTestUtils.<BigDecimal>invokeMethod(
                marketAggregateService, "calculateSalaryMissingRate", 0, 10))
        .isEqualByComparingTo(BigDecimal.valueOf(100.00));
    assertThat(
            ReflectionTestUtils.<BigDecimal>invokeMethod(
                marketAggregateService, "calculateSalaryMissingRate", 10, 10))
        .isEqualByComparingTo(BigDecimal.valueOf(0.00));
    assertThat(
            ReflectionTestUtils.<BigDecimal>invokeMethod(
                marketAggregateService, "calculateSalaryMissingRate", 3, 10))
        .isEqualByComparingTo(BigDecimal.valueOf(70.00));

    // countDisclosedPostings: null salaryMin or null salaryMax
    JobPosting pNoMin = JobPosting.builder().salaryMax(5000L).build();
    JobPosting pNoMax = JobPosting.builder().salaryMin(3000L).build();
    JobPosting pBoth = JobPosting.builder().salaryMin(3000L).salaryMax(5000L).build();
    int count =
        ReflectionTestUtils.invokeMethod(
            marketAggregateService, "countDisclosedPostings", List.of(pNoMin, pNoMax, pBoth));
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("externalIdOrder 파싱 예외 처리 분기 검증")
  void externalIdOrder_handlesNumberFormatException() {
    JobPosting p1 = JobPosting.builder().externalId("non-numeric").build();
    JobPosting p2 = JobPosting.builder().externalId("12345").build();
    long o1 = ReflectionTestUtils.invokeMethod(MarketAggregateService.class, "externalIdOrder", p1);
    long o2 = ReflectionTestUtils.invokeMethod(MarketAggregateService.class, "externalIdOrder", p2);
    assertThat(o1).isEqualTo(Long.MAX_VALUE);
    assertThat(o2).isEqualTo(12345L);
  }
}
