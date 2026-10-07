package com.carix.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.normalize.ExperienceRange;
import com.carix.jobposting.normalize.PostingSalary;
import com.carix.jobposting.seed.JobKoreaListingFields;
import com.carix.jobposting.seed.SaraminListingFields;
import com.carix.pipeline.dto.CrawlRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class JobPostingIngestServiceTest {

  private JobPostingRepository jobPostingRepository;
  private SkillRepository skillRepository;
  private RawIngestRepository rawIngestRepository;
  private JobPostingIngestService service;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    skillRepository = mock(SkillRepository.class);
    rawIngestRepository = mock(RawIngestRepository.class);

    given(skillRepository.findByStandardNameAndCategory(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(skillRepository.save(any(Skill.class))).willAnswer(i -> i.getArgument(0));
    given(jobPostingRepository.save(any(JobPosting.class))).willAnswer(i -> i.getArgument(0));

    service =
        new JobPostingIngestService(jobPostingRepository, skillRepository, rawIngestRepository);
  }

  @Test
  @DisplayName("crawlAndIngest - 유효하지 않은 포털 또는 0페이지 요청 시 안전하게 0 반환")
  void crawlAndIngest_zeroPagesOrInvalid() {
    int res1 = service.crawlAndIngest(new CrawlRequest("NONE", null, 0, null, null, null));
    assertThat(res1).isEqualTo(0);

    int res2 = service.crawlAndIngestJobKorea("IT", 0);
    assertThat(res2).isEqualTo(0);

    int res3 = service.crawlAndIngestSaramin(0);
    assertThat(res3).isEqualTo(0);
  }

  @Test
  @DisplayName("ingestJobKoreaPosting - 신규 공고 적재 (상세페이지 HTML 포함)")
  void ingestJobKoreaPosting_newWithDetail() {
    Instant now = Instant.now();
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "1001"))
        .willReturn(Optional.empty());

    JobKoreaListingFields fields =
        new JobKoreaListingFields("정규직", 4000L, 6000L, "팀원", "우수기업", "~ 09/30(화)");

    String detailHtml =
        """
        <div class="tbList">
          <dl class="tbList">
            <dt>급여</dt><dd>연봉 5,000~7,000만원</dd>
            <dt>직무타입</dt><dd>서버/백엔드개발자</dd>
            <dt>사원수</dt><dd>200명</dd>
            <dt>기업형태</dt><dd>중견기업</dd>
          </dl>
        </div>
        """;

    service.ingestJobKoreaPosting(
        "1001",
        "백엔드 개발자",
        "카카오",
        "서울 강남구",
        ExperienceRange.parse("경력 3~5년"),
        "3일 전",
        fields,
        detailHtml,
        now,
        "202608");

    verify(jobPostingRepository, atLeastOnce()).save(any(JobPosting.class));
  }

  @Test
  @DisplayName("ingestJobKoreaPosting - 신규 공고는 확인 경로 JOBKOREA_LIST 를 단다")
  void ingestJobKoreaPosting_marksListPath() {
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "1005"))
        .willReturn(Optional.empty());
    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);

    service.ingestJobKoreaPosting(
        "1005",
        "백엔드",
        "회사",
        "서울",
        ExperienceRange.parse("신입"),
        "오늘",
        new JobKoreaListingFields("정규직", null, null, null, null, null),
        null,
        Instant.now(),
        "202610");

    verify(jobPostingRepository, atLeastOnce()).save(saved.capture());
    assertThat(saved.getValue().getCollectedVia()).isEqualTo(CollectedVia.JOBKOREA_LIST);
  }

  @Test
  @DisplayName("ingestJobKoreaPosting - 기존 공고 갱신 (상세페이지 HTML 없음)")
  void ingestJobKoreaPosting_existingWithoutDetail() {
    Instant now = Instant.now();
    JobPosting existing = JobPosting.builder().source("JOBKOREA").externalId("1002").build();
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "1002"))
        .willReturn(Optional.of(existing));

    JobKoreaListingFields fields = new JobKoreaListingFields("계약직", 3000L, 4000L, "사원", null, null);

    service.ingestJobKoreaPosting(
        "1002",
        "주니어 개발자",
        "네이버",
        "경기 성남시",
        ExperienceRange.parse("신입"),
        "오늘",
        fields,
        null,
        now,
        "202608");

    assertThat(existing.getLastSeenMonth()).isEqualTo("202608");
    verify(jobPostingRepository, atLeastOnce()).save(existing);
  }

  @Test
  @DisplayName("ingestJobKoreaPosting - 회사내규 공고는 비공개 구간으로, 공개 연봉은 비운다")
  void ingestJobKoreaPosting_bandIsNotPublic() {
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "1003"))
        .willReturn(Optional.empty());
    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
    String detailHtml =
        "<html><head><meta name=\"description\" content=\"경력 : 경력 5년이상, 급여 : 회사 내규에 따름(면접 후 결정), 마감일 : 2026.09.30\"/></head>"
            + "<body><script>self.__next_f.push([1,\"{\\\"pay\\\":{\\\"payType\\\":\\\"ANNUALLY_SALARY\\\",\\\"payRange\\\":{\\\"from\\\":2590,\\\"to\\\":10001,\\\"fromInclusive\\\":true,\\\"toInclusive\\\":false}},\\\"benefitCodes\\\":[]}\"])</script></body></html>";

    service.ingestJobKoreaPosting(
        "1003",
        "백엔드",
        "회사",
        "서울 강남구",
        ExperienceRange.parse("경력 3~5년"),
        "3일 전",
        new JobKoreaListingFields("정규직", null, null, null, null, null),
        detailHtml,
        Instant.now(),
        "202610");

    verify(jobPostingRepository, atLeastOnce()).save(saved.capture());
    JobPosting p = saved.getValue();
    assertThat(p.getSalaryMin()).isNull();
    assertThat(p.getSalaryBandMin()).isEqualTo(25_900_000L);
    assertThat(p.getSalaryDisclosure()).isEqualTo("BAND");
  }

  @Test
  @DisplayName("ingestJobKoreaPosting - 기존 공고를 상세페이지 없이 다시 봐도 앞서 판정한 급여를 지우지 않는다")
  void ingestJobKoreaPosting_existingKeepsVerdictWithoutDetail() {
    JobPosting existing = JobPosting.builder().source("JOBKOREA").externalId("1004").build();
    existing.applySalary(PostingSalary.publicRange(50_000_000L, 70_000_000L));
    given(jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "1004"))
        .willReturn(Optional.of(existing));

    service.ingestJobKoreaPosting(
        "1004",
        "백엔드",
        "회사",
        "서울",
        ExperienceRange.parse("신입"),
        "오늘",
        new JobKoreaListingFields("정규직", null, null, null, null, null),
        null,
        Instant.now(),
        "202610");

    assertThat(existing.getSalaryMin()).isEqualTo(50_000_000L);
    assertThat(existing.getSalaryMax()).isEqualTo(70_000_000L);
    assertThat(existing.getSalaryDisclosure()).isEqualTo("PUBLIC");
  }

  @Test
  @DisplayName("ingestSaraminPosting - 신규 공고에 상세페이지 판정 결과를 넣는다")
  void ingestSaraminPosting_appliesSalaryForNew() {
    given(jobPostingRepository.findBySourceAndExternalId("SARAMIN", "2004"))
        .willReturn(Optional.empty());
    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);

    service.ingestSaraminPosting(
        "2004",
        "하드웨어 개발자",
        "옵티시스",
        "경기 성남시",
        ExperienceRange.parse("경력무관"),
        "26/10/01",
        new SaraminListingFields("정규직", "경력무관", "~ 11/14(금)", List.of(), List.of()),
        Instant.now(),
        "202610",
        PostingSalary.publicRange(41_000_000L, 41_000_000L));

    verify(jobPostingRepository, atLeastOnce()).save(saved.capture());
    assertThat(saved.getValue().getSalaryMin()).isEqualTo(41_000_000L);
    assertThat(saved.getValue().getSalaryDisclosure()).isEqualTo("PUBLIC");
  }

  @Test
  @DisplayName("ingestSaraminPosting - 기존 공고는 salaryForNew 가 null 이면 급여를 건드리지 않는다")
  void ingestSaraminPosting_existingSalaryUntouched() {
    JobPosting existing = JobPosting.builder().source("SARAMIN").externalId("2005").build();
    existing.applySalary(PostingSalary.publicRange(41_000_000L, 41_000_000L));
    given(jobPostingRepository.findBySourceAndExternalId("SARAMIN", "2005"))
        .willReturn(Optional.of(existing));

    service.ingestSaraminPosting(
        "2005",
        "개발자",
        "회사",
        "서울",
        ExperienceRange.parse("무관"),
        null,
        new SaraminListingFields("정규직", "무관", null, List.of(), List.of()),
        Instant.now(),
        "202610",
        null);

    assertThat(existing.getSalaryMin()).isEqualTo(41_000_000L);
    assertThat(existing.getSalaryDisclosure()).isEqualTo("PUBLIC");
  }

  @Test
  @DisplayName("ingestSaraminPosting - 사람인 신규 공고 적재 및 스킬/산업 태그 저장")
  void ingestSaraminPosting_new() {
    Instant now = Instant.now();
    given(jobPostingRepository.findBySourceAndExternalId("SARAMIN", "2001"))
        .willReturn(Optional.empty());

    SaraminListingFields fields =
        new SaraminListingFields(
            "정규직", "경력 3~5년", "~ 08/31(월)", List.of("Java", "Spring Boot"), List.of("SI"));

    service.ingestSaraminPosting(
        "2001",
        "자바 개발자",
        "삼성SDS",
        "서울 송파구",
        ExperienceRange.parse("경력 3~5년"),
        "26/08/01",
        fields,
        now,
        "202608",
        null);

    verify(jobPostingRepository, atLeastOnce()).save(any(JobPosting.class));
    verify(skillRepository, atLeastOnce()).save(any(Skill.class));
  }

  @Test
  @DisplayName("ingestSaraminPosting - 사람인 기존 공고 갱신")
  void ingestSaraminPosting_existing() {
    Instant now = Instant.now();
    JobPosting existing = JobPosting.builder().source("SARAMIN").externalId("2002").build();
    given(jobPostingRepository.findBySourceAndExternalId("SARAMIN", "2002"))
        .willReturn(Optional.of(existing));

    SaraminListingFields fields = new SaraminListingFields("계약직", "무관", null, List.of(), List.of());

    service.ingestSaraminPosting(
        "2002",
        "데이터 엔지니어",
        "라인",
        "전국",
        ExperienceRange.parse("무관"),
        null,
        fields,
        now,
        "202608",
        null);

    assertThat(existing.getLastSeenMonth()).isEqualTo("202608");
    verify(jobPostingRepository, atLeastOnce()).save(existing);
  }

  @Test
  @DisplayName("ingestSaraminPosting - 기존에 존재하는 스킬이 있는 경우 재사용")
  void ingestSaraminPosting_withExistingSkill() {
    Instant now = Instant.now();
    Skill existingSkill = new Skill("Java", "Backend", false);
    given(skillRepository.findByStandardNameAndCategory("Java", "Backend"))
        .willReturn(Optional.of(existingSkill));

    SaraminListingFields fields =
        new SaraminListingFields("정규직", "신입", null, List.of("Java"), List.of("IT"));

    service.ingestSaraminPosting(
        "2003",
        "자바 주니어",
        "당근",
        "서울 서초구",
        ExperienceRange.parse("신입"),
        null,
        fields,
        now,
        "202608",
        null);

    verify(jobPostingRepository, atLeastOnce()).save(any(JobPosting.class));
  }

  @Test
  @DisplayName("refineAllPostingSkills - 전체 공고 스킬 일괄 재정제 및 사전 캐시 자동 갱신 정상 수행")
  void refineAllPostingSkills_success() {
    Skill javaSkill = new Skill("Java", "LANGUAGE", false);
    given(skillRepository.findAll()).willReturn(List.of(javaSkill));
    given(skillRepository.findFirstByStandardNameIgnoreCase("Java"))
        .willReturn(Optional.of(javaSkill));

    JobPosting posting =
        JobPosting.builder().source("JOBKOREA").externalId("1001").title("백엔드 Java 개발자 채용").build();
    given(jobPostingRepository.findAll()).willReturn(List.of(posting));

    Map<String, Object> stats = service.refineAllPostingSkills();

    assertThat(stats).isNotNull();
    assertThat(stats.get("totalPostings")).isEqualTo(1);
    assertThat(stats.get("refinedPostings")).isEqualTo(1);
    verify(jobPostingRepository, atLeastOnce()).save(posting);
  }

  @Test
  @DisplayName("deduplicateSkills - 벌크 Repository가 제공될 때 중복 스킬 링크 재할당 및 삭제 정상 수행")
  void deduplicateSkills_withBulkRepositories() {
    com.carix.jobposting.JobPostingSkillRepository jpsRepo =
        mock(com.carix.jobposting.JobPostingSkillRepository.class);
    com.carix.jobposting.SkillAliasRepository saRepo =
        mock(com.carix.jobposting.SkillAliasRepository.class);
    com.carix.jobposting.UserSkillRepository usRepo =
        mock(com.carix.jobposting.UserSkillRepository.class);

    Skill canonical = new Skill("React", "FRAMEWORK", false);
    Skill duplicate = new Skill("React", "SKILL", false);

    given(skillRepository.findAll()).willReturn(List.of(canonical, duplicate));

    JobPostingIngestService bulkService =
        new JobPostingIngestService(
            jobPostingRepository,
            skillRepository,
            rawIngestRepository,
            jpsRepo,
            saRepo,
            usRepo,
            null);

    int deduped = bulkService.deduplicateSkills();

    assertThat(deduped).isEqualTo(1);
    verify(jpsRepo).deleteDuplicateLinks(duplicate.getId(), canonical.getId());
    verify(jpsRepo).reassignSkill(duplicate.getId(), canonical);
    verify(saRepo).reassignSkill(duplicate.getId(), canonical);
    verify(usRepo).deleteDuplicateLinks(duplicate.getId(), canonical.getId());
    verify(usRepo).reassignSkill(duplicate.getId(), canonical);
    verify(skillRepository).deleteSkillDirectly(duplicate.getId());
  }

  @Test
  @DisplayName("사람인 신규 공고는 확인 경로 SARAMIN_SEARCH 를 단다")
  void saraminNewPostingMarksSearchPath() {
    given(jobPostingRepository.findBySourceAndExternalId("SARAMIN", "501"))
        .willReturn(Optional.empty());
    service.ingestSaraminPosting(
        "501",
        "백엔드 개발자",
        "회사",
        "서울",
        ExperienceRange.parse("경력 3년↑"),
        null,
        new SaraminListingFields("경력 3년↑", "정규직", null, List.of("Java"), List.of()),
        Instant.now(),
        "202610",
        null);
    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
    verify(jobPostingRepository, atLeastOnce()).save(saved.capture());
    assertThat(saved.getValue().getCollectedVia()).isEqualTo("SARAMIN_SEARCH");
  }

  @Test
  @DisplayName("점핏으로 보강된 사람인 공고는 사람인 재수집이 스킬을 지우지 않는다")
  void saraminRecrawlKeepsJumpitSkills() {
    JobPosting existing =
        new JobPosting(
            "SARAMIN",
            "502",
            "백엔드",
            "회사",
            null,
            null,
            "서울",
            null,
            null,
            "UNKNOWN",
            "UNKNOWN",
            null,
            null,
            "DEVELOPER",
            null,
            null,
            "202609",
            "202609",
            Instant.now());
    Skill kafka = new Skill("Kafka", "SKILL", false);
    ReflectionTestUtils.setField(kafka, "id", UUID.randomUUID());
    existing.addSkill(kafka);
    existing.addCollectedVia(CollectedVia.JUMPIT);
    // 새로 만든 스킬도 연결할 수 있게 id 를 붙인다.
    given(skillRepository.save(any(Skill.class)))
        .willAnswer(
            i -> {
              Skill created = i.getArgument(0);
              ReflectionTestUtils.setField(created, "id", UUID.randomUUID());
              return created;
            });
    given(jobPostingRepository.findBySourceAndExternalId("SARAMIN", "502"))
        .willReturn(Optional.of(existing));

    service.ingestSaraminPosting(
        "502",
        "백엔드",
        "회사",
        "서울",
        ExperienceRange.parse("경력무관"),
        null,
        new SaraminListingFields("경력무관", "정규직", null, List.of("Java"), List.of()),
        Instant.now(),
        "202610",
        null);

    assertThat(existing.getSkills()).extracting(Skill::getStandardName).contains("Kafka", "Java");
    assertThat(existing.getCollectedVia())
        .isEqualTo(CollectedVia.JUMPIT + "," + CollectedVia.SARAMIN_SEARCH);
  }
}
