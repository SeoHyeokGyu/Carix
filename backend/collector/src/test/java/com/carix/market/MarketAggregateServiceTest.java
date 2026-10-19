package com.carix.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
  @DisplayName("aggregateMarketData(request) - 연도 구간 및 전체 요청 시 모든 대상 월에 대해 멱등 집계를 실행한다")
  void aggregateMarketData_withRequestOptions() {
    // Given
    JobPosting p =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .firstSeenMonth("202501")
            .lastSeenMonth("202503")
            .collectedAt(Instant.now())
            .build();
    when(jobPostingRepository.findAll()).thenReturn(List.of(p));

    // When: 전체(all=true) 집계 요청
    int count =
        marketAggregateService.aggregateMarketData(
            new com.carix.pipeline.dto.MarketAggregateRequest(null, null, null, null, true));

    // Then: 202501, 202503에 대해 delete 및 save 실행
    assertThat(count).isGreaterThan(0);
    verify(marketAggregateRepository).deleteByBaseYearMonth("202501");
    verify(marketAggregateRepository).deleteByBaseYearMonth("202503");
  }

  @Test
  @DisplayName("aggregateMarketData - null 요청 및 빈 데이터 처리")
  void aggregateMarketData_emptyAndNull() {
    when(jobPostingRepository.findAll()).thenReturn(List.of());

    assertThat(marketAggregateService.aggregateMarketData()).isEqualTo(0);
    assertThat(
            marketAggregateService.aggregateMarketData(
                (com.carix.pipeline.dto.MarketAggregateRequest) null))
        .isEqualTo(0);
  }

  @Test
  @DisplayName("aggregateMarketData - month, year, yearRange 조건 분기")
  void aggregateMarketData_branches() {
    JobPosting p =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .firstSeenMonth("202501")
            .lastSeenMonth("202503")
            .collectedAt(Instant.now())
            .build();
    when(jobPostingRepository.findAll()).thenReturn(List.of(p));

    int resMonth =
        marketAggregateService.aggregateMarketData(
            new com.carix.pipeline.dto.MarketAggregateRequest("2025-01", null, null, null, null));
    assertThat(resMonth).isGreaterThan(0);

    int resYear =
        marketAggregateService.aggregateMarketData(
            new com.carix.pipeline.dto.MarketAggregateRequest(null, "2025", null, null, null));
    assertThat(resYear).isGreaterThan(0);

    int resRange =
        marketAggregateService.aggregateMarketData(
            new com.carix.pipeline.dto.MarketAggregateRequest(null, null, "2024", "2025", null));
    assertThat(resRange).isGreaterThan(0);

    // endYear null 인 경우 startYear 로 단일연도 처리
    int resSingleRange =
        marketAggregateService.aggregateMarketData(
            new com.carix.pipeline.dto.MarketAggregateRequest(null, null, "2025", null, null));
    assertThat(resSingleRange).isGreaterThan(0);

    // month, year, startYear 가 빈 문자열(blank)인 경우 다음 조건으로 폴백
    int resBlankFallback =
        marketAggregateService.aggregateMarketData(
            new com.carix.pipeline.dto.MarketAggregateRequest("  ", "  ", "  ", null, false));
    assertThat(resBlankFallback).isGreaterThan(0);
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
  @DisplayName("b18 - filterActivePostingsForMonth null 및 빈 컬렉션 방어")
  void b18_filterActivePostingsForMonth_nullAndEmpty() {
    assertThat(marketAggregateService.filterActivePostingsForMonth(null, "202608")).isEmpty();
    assertThat(marketAggregateService.filterActivePostingsForMonth(List.of(), "202608")).isEmpty();
    assertThat(
            marketAggregateService.filterActivePostingsForMonth(
                List.of(JobPosting.builder().build()), null))
        .isEmpty();
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
  @DisplayName("aggregateMarketDataForYearRange - endYear 가 빈 문자열(blank)인 경우 startYear 단일 연도로 동작")
  void aggregateMarketDataForYearRange_blankEndYear() {
    JobPosting p =
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("jk-1")
            .jobFamily("BACKEND")
            .firstSeenMonth("202501")
            .lastSeenMonth("202501")
            .build();
    when(jobPostingRepository.findAll()).thenReturn(List.of(p));

    int count = marketAggregateService.aggregateMarketDataForYearRange("2025", "   ");
    assertThat(count).isGreaterThan(0);
  }

  @Test
  @DisplayName(
      "aggregateMarketDataForMonths 및 aggregateMarketDataForMonth - 공고 또는 월 목록이 비어있는 경우 조기 반환(0)")
  void earlyReturnWhenPostingsOrMonthsEmpty() {
    when(jobPostingRepository.findAll()).thenReturn(List.of());

    // 1. 공고 없음 -> aggregateMarketDataForMonth 0
    assertThat(marketAggregateService.aggregateMarketDataForMonth("202608")).isEqualTo(0);

    // 2. 공고 없음 -> aggregateMarketDataForMonths 0
    assertThat(marketAggregateService.aggregateMarketDataForMonths(List.of("202608"))).isEqualTo(0);

    // 3. 공고는 있으나 월 목록이 빈 경우
    JobPosting p = JobPosting.builder().source("JOBKOREA").externalId("jk-1").build();
    when(jobPostingRepository.findAll()).thenReturn(List.of(p));
    assertThat(marketAggregateService.aggregateMarketDataForMonths(List.of())).isEqualTo(0);
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

  /** 사람인 검색 경로로 수집한 공고(측정 프레임 SARAMIN 행의 구성원). */
  private static JobPosting saraminSearch(String externalId, String company, String seenMonth) {
    JobPosting p = posting("SARAMIN", externalId, company, seenMonth, null, null);
    p.addCollectedVia(CollectedVia.SARAMIN_SEARCH);
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
  @DisplayName("원티드 원천 집계도 그 달 표본만 센다 — 정기 수집분은 ALL 에만, 잡코리아·사람인은 그대로")
  void wantedSourceCountsOnlySampledPostingsOfMonth() {
    List<JobPosting> postings =
        List.of(
            posting("JOBKOREA", "jk1", "가", "202608", "202608", null),
            posting("JOBKOREA", "jk2", "나", "202608", "202608", null),
            posting("WANTED", "w1", "다", "202608", "202608", null),
            posting("WANTED", "w2", "라", "202608", "202608", null),
            posting("WANTED", "w3", "마", "202608", null, null),
            posting("WANTED", "w4", "바", "202608", null, null),
            posting("WANTED", "w5", "사", "202608", null, null),
            saraminSearch("s1", "아", "202608"),
            saraminSearch("s2", "자", "202608"),
            saraminSearch("s3", "차", "202608"));

    List<MarketAggregate> saved = aggregate("202608", postings);

    assertThat(total(saved, "WANTED").getPostingCount()).isEqualTo(2);
    assertThat(total(saved, "JOBKOREA").getPostingCount()).isEqualTo(2);
    assertThat(total(saved, "SARAMIN").getPostingCount()).isEqualTo(3);
    assertThat(total(saved, "ALL").getPostingCount()).isEqualTo(10);
  }

  @Test
  @DisplayName("원티드 표본도 같은 회사를 월 3건까지만 센다")
  void wantedSampleCapsPostingsPerCompanyPerMonth() {
    List<JobPosting> postings =
        List.of(
            posting("WANTED", "10", "㈜조인아이티", "202608", "202608", null),
            posting("WANTED", "11", "조인아이티", "202608", "202608", null),
            posting("WANTED", "12", "(주)조인아이티", "202608", "202608", null),
            posting("WANTED", "13", "조인아이티", "202608", "202608", null));

    List<MarketAggregate> saved = aggregate("202608", postings);

    assertThat(total(saved, "WANTED").getPostingCount()).isEqualTo(3);
  }
}
