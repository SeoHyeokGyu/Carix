package com.carix.jobposting.jumpit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.crawler.SaraminDetailFetcher;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.JobPostingIngestService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class JumpitIngestServiceTest {

  private JobPostingRepository postings;
  private RawIngestRepository rawIngests;
  private JobPostingIngestService skills;
  private SkillTaxonomy taxonomy;
  private SaraminDetailFetcher saramin;
  private FakeClient client;
  private JumpitIngestService service;

  /** 목록 1페이지는 고정 조각, 2페이지는 같은 조각(중복 공고), 3페이지는 빈 페이지. */
  static final class FakeClient implements JumpitClient {
    final String page;
    final String empty;
    String detail;
    int detailFetches;
    Runnable onFetchDetail = () -> {};

    FakeClient(String page, String empty, String detail) {
      this.page = page;
      this.empty = empty;
      this.detail = detail;
    }

    @Override
    public Optional<String> fetchListPage(int n) {
      return Optional.of(n <= 2 ? page : empty);
    }

    @Override
    public Optional<String> fetchDetail(String id) {
      detailFetches++;
      onFetchDetail.run();
      return Optional.ofNullable(detail);
    }
  }

  private static String fixture(String name) throws IOException {
    try (var in = JumpitIngestServiceTest.class.getResourceAsStream("/jumpit/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Skill skill(String name) {
    Skill s = new Skill(name, "SKILL", false);
    ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
    return s;
  }

  @BeforeEach
  void setUp() throws IOException {
    postings = mock(JobPostingRepository.class);
    rawIngests = mock(RawIngestRepository.class);
    skills = mock(JobPostingIngestService.class);
    taxonomy = mock(SkillTaxonomy.class);
    saramin = mock(SaraminDetailFetcher.class);
    given(postings.save(any(JobPosting.class))).willAnswer(i -> i.getArgument(0));
    given(rawIngests.findBySourceAndSourceItemId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(taxonomy.matchKnownSkills(anyString())).willReturn(List.of());
    given(taxonomy.matchKnownSkills("Java")).willReturn(List.of("Java"));
    given(taxonomy.matchKnownSkills("Spring Boot")).willReturn(List.of("Spring Boot"));
    given(skills.upsertKnownSkill(anyString())).willAnswer(i -> skill(i.getArgument(0)));
    given(saramin.fetch(anyString())).willReturn(Optional.empty());
    client =
        new FakeClient(
            fixture("positions-page.json"),
            fixture("positions-empty.json"),
            fixture("position-detail.json"));
    service =
        new JumpitIngestService(
            client,
            new JumpitParser(),
            new JumpitFamilyResolver(),
            postings,
            rawIngests,
            skills,
            taxonomy,
            saramin,
            TransactionOperations.withoutTransaction());
  }

  @Test
  @DisplayName("없는 공고는 사람인 행으로 새로 만들고 게시·마감일·경로·태그를 채운다")
  void createsSaraminRow() {
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());

    JumpitIngestSummary summary = service.ingestAll();

    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
    verify(postings, org.mockito.Mockito.times(2)).save(saved.capture());
    JobPosting first = saved.getAllValues().get(0);
    assertThat(first.getSource()).isEqualTo("SARAMIN");
    assertThat(first.getExternalId()).isEqualTo("55220505");
    assertThat(first.getCollectedVia()).isEqualTo("JUMPIT");
    assertThat(first.getJobFamily()).isEqualTo("DATA");
    assertThat(first.getSourceJobTags()).isEqualTo("서버/백엔드 개발자,빅데이터 엔지니어");
    assertThat(first.getPostedStartDate()).isEqualTo(LocalDate.of(2026, 10, 7));
    assertThat(first.getPostedEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    assertThat(first.getSkills())
        .extracting(Skill::getStandardName)
        .containsExactly("Java", "Spring Boot");

    assertThat(summary.created()).isEqualTo(2);
    assertThat(summary.enriched()).isZero();
    // 같은 조각이 두 페이지에 나와 id 없는 항목도 페이지마다 한 번씩 센다
    assertThat(summary.skipped()).isEqualTo(2);
    assertThat(summary.droppedSkills()).containsEntry("ESET", 1);
  }

  @Test
  @DisplayName("상세 응답이 JSON 이 아니면 게시일만 비우고 공고는 만든다")
  void unparsableDetailKeepsPosting() {
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    client.detail = "<html>oops</html>";

    JumpitIngestSummary summary = service.ingestAll();

    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
    verify(postings, org.mockito.Mockito.times(2)).save(saved.capture());
    assertThat(saved.getAllValues()).allSatisfy(p -> assertThat(p.getPostedStartDate()).isNull());
    assertThat(summary.created()).isEqualTo(2);
    assertThat(summary.errors()).isZero();
  }

  @Test
  @DisplayName("같은 공고가 다음 페이지에 또 나와도 한 번만 처리한다")
  void processesEachIdOnce() {
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    JumpitIngestSummary summary = service.ingestAll();
    assertThat(summary.pages()).isEqualTo(2);
    assertThat(summary.fetched()).isEqualTo(2);
  }

  @Test
  @DisplayName("있는 사람인 공고는 스킬만 더하고 판정된 직무·급여·경력은 그대로 둔다")
  void enrichesExistingWithoutOverwriting() {
    JobPosting existing =
        new JobPosting(
            "SARAMIN",
            "55220505",
            "백엔드",
            "회사",
            null,
            null,
            "서울",
            1,
            3,
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
    existing.addSkill(skill("Kafka"));
    existing.addCollectedVia("SARAMIN_SEARCH");
    String disclosureBefore = existing.getSalaryDisclosure();
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(postings.findBySourceAndExternalId("SARAMIN", "55220505"))
        .willReturn(Optional.of(existing));

    JumpitIngestSummary summary = service.ingestAll();

    assertThat(existing.getJobFamily()).isEqualTo("DEVELOPER");
    assertThat(existing.getExperienceMin()).isEqualTo(1);
    assertThat(existing.getSalaryDisclosure()).isEqualTo(disclosureBefore);
    assertThat(existing.getSkills())
        .extracting(Skill::getStandardName)
        .containsExactly("Kafka", "Java", "Spring Boot");
    assertThat(existing.getCollectedVia()).isEqualTo("SARAMIN_SEARCH,JUMPIT");
    assertThat(existing.getLastSeenMonth()).isNotEqualTo("202609");
    assertThat(summary.enriched()).isEqualTo(1);
    verify(saramin, never()).fetch("55220505");
    // 55220506 은 새 공고라 상세를 받지만, 있는 공고(55220505)는 받지 않는다 — 페이지가 둘이어도 새 공고 한 번이다.
    assertThat(client.detailFetches).isEqualTo(1);
  }

  @Test
  @DisplayName("점핏 상세·사람인 상세 HTTP 호출은 트랜잭션 밖에서 일어난다")
  void httpCallsHappenOutsideTransaction() {
    AtomicBoolean inTx = new AtomicBoolean(false);
    List<Boolean> observed = new ArrayList<>();
    TransactionOperations flagging =
        new TransactionOperations() {
          @Override
          public <T> T execute(TransactionCallback<T> action) {
            inTx.set(true);
            try {
              return action.doInTransaction(mock(TransactionStatus.class));
            } finally {
              inTx.set(false);
            }
          }
        };
    client.onFetchDetail = () -> observed.add(inTx.get());
    given(saramin.fetch(anyString()))
        .willAnswer(
            i -> {
              observed.add(inTx.get());
              return Optional.empty();
            });
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    JumpitIngestService flaggedService =
        new JumpitIngestService(
            client,
            new JumpitParser(),
            new JumpitFamilyResolver(),
            postings,
            rawIngests,
            skills,
            taxonomy,
            saramin,
            flagging);

    JumpitIngestSummary summary = flaggedService.ingestAll();

    assertThat(summary.created()).isEqualTo(2);
    assertThat(observed).hasSize(4).containsOnly(false);
  }

  @Test
  @DisplayName("상세 API 가 비면 게시일 없이 저장하고 계속한다")
  void missingDetailKeepsGoing() {
    client.detail = null;
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    JumpitIngestSummary summary = service.ingestAll();
    assertThat(summary.created()).isEqualTo(2);
  }

  @Test
  @DisplayName("목록 페이지마다 수집 이력을 JUMPIT 으로 남긴다")
  void recordsRawIngestAsJumpit() {
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    service.ingestAll();
    ArgumentCaptor<RawIngest> raw = ArgumentCaptor.forClass(RawIngest.class);
    verify(rawIngests, org.mockito.Mockito.atLeastOnce()).save(raw.capture());
    assertThat(raw.getAllValues()).allSatisfy(r -> assertThat(r.getSource()).isEqualTo("JUMPIT"));
  }

  @Test
  @DisplayName("공고 한 건 처리가 실패해도 나머지를 계속 처리하고 errors 로 센다")
  void failedPositionDoesNotAbortRun() {
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(postings.save(any(JobPosting.class)))
        .willAnswer(
            i -> {
              JobPosting p = i.getArgument(0);
              if ("55220506".equals(p.getExternalId())) {
                throw new IllegalStateException("boom");
              }
              return p;
            });

    JumpitIngestSummary summary = service.ingestAll();

    assertThat(summary.errors()).isEqualTo(1);
    assertThat(summary.created()).isEqualTo(1);
    assertThat(summary.fetched()).isEqualTo(2);
  }

  @Test
  @DisplayName("사전 확인 뒤 행이 사라져 급여를 못 받았어도 급여 미상으로 새로 만든다")
  void createsWithUnknownSalaryWhenRowVanishes() {
    JobPosting ghost =
        new JobPosting(
            "SARAMIN",
            "55220505",
            "t",
            "c",
            null,
            null,
            "서울",
            1,
            3,
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
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(postings.findBySourceAndExternalId("SARAMIN", "55220505"))
        .willReturn(Optional.of(ghost), Optional.empty());

    JumpitIngestSummary summary = service.ingestAll();

    assertThat(summary.errors()).isZero();
    assertThat(summary.created()).isEqualTo(2);
  }

  @Test
  @DisplayName("요약은 작업 details 맵으로 바뀐다")
  void summaryToDetails() {
    Map<String, Object> d =
        new JumpitIngestSummary(2, 2, 1, 1, 0, 3, Map.of("ESET", 1)).toDetails();
    assertThat(d)
        .containsEntry("errors", 3)
        .containsEntry("created", 1)
        .containsEntry("droppedSkills", Map.of("ESET", 1));
  }

  @Test
  @DisplayName("점핏 포지션에 직무 카테고리가 없으면 기존 공고의 출처 직무 태그를 지우지 않는다")
  void emptyCategoriesKeepExistingSourceTags() {
    JobPosting existing = jobPosting("55220506");
    existing.recordSourceJobTags("X");
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(postings.findBySourceAndExternalId("SARAMIN", "55220506"))
        .willReturn(Optional.of(existing));

    service.ingestAll();

    assertThat(existing.getSourceJobTags()).isEqualTo("X");
  }

  @Test
  @DisplayName("저장이 실패한 공고의 미등록 기술 스택은 droppedSkills 에 세지 않는다")
  void failedPositionDoesNotCountDroppedSkills() {
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(postings.save(any(JobPosting.class)))
        .willAnswer(
            i -> {
              JobPosting p = i.getArgument(0);
              if ("55220505".equals(p.getExternalId())) {
                throw new IllegalStateException("boom");
              }
              return p;
            });

    JumpitIngestSummary summary = service.ingestAll();

    assertThat(summary.errors()).isEqualTo(1);
    assertThat(summary.droppedSkills()).doesNotContainKey("ESET");
  }

  private static JobPosting jobPosting(String externalId) {
    return new JobPosting(
        "SARAMIN",
        externalId,
        "t",
        "c",
        null,
        null,
        "서울",
        1,
        3,
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
  }
}
