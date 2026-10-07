package com.carix.jobposting.wanted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngest;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

class WantedIngestServiceTest {

  private static final String EMPTY_LIST = "{\"links\":{\"next\":null},\"data\":[]}";

  private JobPostingRepository postings;
  private RawIngestRepository rawIngests;
  private PostingSkillService skills;
  private SkillTaxonomy taxonomy;
  private FakeClient client;
  private WantedIngestService service;

  /** 목록 offset 0 은 list.json, 그 뒤는 빈 목록. 페이지는 요청한 id 로 바꿔 돌려준다. */
  static final class FakeClient implements WantedClient {
    final String list;
    String page;
    String detail;
    int pageFetches;
    int detailFetches;
    boolean rewriteId = true;
    int failListAtOffset = -1;

    FakeClient(String list, String page, String detail) {
      this.list = list;
      this.page = page;
      this.detail = detail;
    }

    @Override
    public Optional<String> fetchList(int offset) {
      if (offset == failListAtOffset) {
        return Optional.empty();
      }
      return Optional.of(offset == 0 ? list : EMPTY_LIST);
    }

    @Override
    public Optional<String> fetchPage(long id) {
      pageFetches++;
      return Optional.ofNullable(page)
          .map(p -> rewriteId ? p.replace("391003", String.valueOf(id)) : p);
    }

    @Override
    public Optional<String> fetchDetailApi(long id) {
      detailFetches++;
      return Optional.ofNullable(detail);
    }
  }

  private static String fixture(String name) throws IOException {
    try (var in = WantedIngestServiceTest.class.getResourceAsStream("/wanted/" + name)) {
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
    skills = mock(PostingSkillService.class);
    taxonomy = mock(SkillTaxonomy.class);
    given(postings.save(any(JobPosting.class))).willAnswer(i -> i.getArgument(0));
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(rawIngests.findBySourceAndSourceItemId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(taxonomy.matchKnownSkills(anyString())).willReturn(List.of());
    given(taxonomy.matchKnownSkills("Kotlin")).willReturn(List.of("Kotlin"));
    given(taxonomy.matchKnownSkills("AWS")).willReturn(List.of("AWS"));
    given(taxonomy.extractSkillsFromSentence(anyString())).willReturn(List.of());
    given(skills.upsertKnownSkill(anyString())).willAnswer(i -> skill(i.getArgument(0)));
    client =
        new FakeClient(
            fixture("list.json"), fixture("page-active.html"), fixture("detail-api.json"));
    service = newService(TransactionOperations.withoutTransaction());
  }

  private WantedIngestService newService(TransactionOperations tx) {
    return new WantedIngestService(
        client, new WantedParser(), postings, rawIngests, skills, taxonomy, tx);
  }

  @Test
  @DisplayName("새 공고는 페이지·상세 API 를 열어 WANTED 행을 만든다")
  void createsNewPosting() {
    WantedCrawlSummary summary = service.ingestAll();

    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
    verify(postings, times(3)).save(saved.capture());
    JobPosting first = saved.getAllValues().get(0);
    String month = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    assertThat(first.getSource()).isEqualTo("WANTED");
    assertThat(first.getExternalId()).isEqualTo("391003");
    assertThat(first.getCollectedVia()).isEqualTo("WANTED_LIST");
    assertThat(first.getSalaryDisclosure()).isEqualTo("NONE");
    assertThat(first.getPostedStartDate()).isEqualTo(LocalDate.of(2026, 10, 7));
    assertThat(first.getFirstSeenMonth()).isEqualTo(month);
    assertThat(first.getLastSeenMonth()).isEqualTo(month);
    assertThat(first.getSampledMonth()).isNull();
    assertThat(first.getSourceJobTags()).isEqualTo("1634:머신러닝 엔지니어");
    assertThat(first.getSkills()).extracting(Skill::getStandardName).contains("Kotlin", "AWS");
    assertThat(summary.created()).isEqualTo(3);
    assertThat(summary.pages()).isEqualTo(1);
    assertThat(summary.errors()).isZero();
    assertThat(summary.droppedSkills()).containsEntry("Node.js", 3).containsEntry("Golang", 3);
  }

  @Test
  @DisplayName("목록 페이지마다 수집 이력을 WANTED 로 남긴다")
  void recordsRawIngestAsWanted() {
    service.ingestAll();
    ArgumentCaptor<RawIngest> raw = ArgumentCaptor.forClass(RawIngest.class);
    verify(rawIngests, org.mockito.Mockito.atLeastOnce()).save(raw.capture());
    assertThat(raw.getAllValues()).allSatisfy(r -> assertThat(r.getSource()).isEqualTo("WANTED"));
  }

  @Test
  @DisplayName("이미 있는 공고는 HTTP 없이 관측월과 확인 경로만 갱신한다")
  void existingPostingTouchesOnly() {
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willAnswer(i -> Optional.of(existing(i.getArgument(1))));

    WantedCrawlSummary summary = service.ingestAll();

    assertThat(summary.seen()).isEqualTo(3);
    assertThat(summary.created()).isZero();
    assertThat(client.pageFetches).isZero();
    assertThat(client.detailFetches).isZero();
  }

  @Test
  @DisplayName("페이지에 __NEXT_DATA__ 가 없으면 skipped 로 세고 계속한다")
  void noDataPageSkipped() throws IOException {
    client.page = fixture("page-nodata.html");

    WantedCrawlSummary summary = service.ingestAll();

    assertThat(summary.skipped()).isEqualTo(3);
    assertThat(summary.errors()).isZero();
    assertThat(summary.created()).isZero();
  }

  @Test
  @DisplayName("개발(518) 이 아닌 직군 페이지는 저장하지 않고 skipped 로 센다")
  void nonDevelopmentPageSkipped() throws IOException {
    client.page = fixture("page-closed.html").replace("315000", "391003");

    WantedCrawlSummary summary = service.ingestAll();

    assertThat(summary.skipped()).isEqualTo(3);
    assertThat(summary.created()).isZero();
    verify(postings, org.mockito.Mockito.never()).save(any(JobPosting.class));
  }

  @Test
  @DisplayName("상세 API 가 비면 스킬 태그 없이 본문 추출 스킬로 저장한다")
  void missingDetailApiStillSaves() {
    client.detail = null;
    given(taxonomy.extractSkillsFromSentence(anyString())).willReturn(List.of("Spring"));

    WantedCrawlSummary summary = service.ingestAll();

    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
    verify(postings, times(3)).save(saved.capture());
    assertThat(saved.getAllValues().get(0).getSkills())
        .extracting(Skill::getStandardName)
        .containsExactly("Spring");
    assertThat(summary.created()).isEqualTo(3);
    assertThat(summary.errors()).isZero();
  }

  @Test
  @DisplayName("한 공고 저장이 실패해도 나머지를 저장하고 errors 로 센다; 실패한 공고의 버린 스킬은 세지 않는다")
  void failureIsolated() {
    given(postings.save(any(JobPosting.class)))
        .willAnswer(
            i -> {
              JobPosting p = i.getArgument(0);
              if ("391002".equals(p.getExternalId())) {
                throw new IllegalStateException("boom");
              }
              return p;
            });

    WantedCrawlSummary summary = service.ingestAll();

    assertThat(summary.created()).isEqualTo(2);
    assertThat(summary.errors()).isEqualTo(1);
    assertThat(summary.droppedSkills()).containsEntry("Node.js", 2);
  }

  @Test
  @DisplayName("페이지 공고 번호가 목록 번호와 다르면 저장하지 않고 skipped 로 센다")
  void pageIdMismatchSkipped() {
    client.rewriteId = false;

    WantedCrawlSummary summary = service.ingestAll();

    // 391003 은 일치, 나머지 둘은 불일치
    assertThat(summary.created()).isEqualTo(1);
    assertThat(summary.skipped()).isEqualTo(2);
    ArgumentCaptor<JobPosting> saved = ArgumentCaptor.forClass(JobPosting.class);
    verify(postings, times(1)).save(saved.capture());
    assertThat(saved.getValue().getExternalId()).isEqualTo("391003");
  }

  @Test
  @DisplayName("같은 목록 번호가 한 실행에서 반복돼도 한 번만 처리한다")
  void repeatedListIdProcessedOnce() throws IOException {
    String one = fixture("list.json");
    client =
        new FakeClient(
            one.replace("391002", "391003"),
            fixture("page-active.html"),
            fixture("detail-api.json"));
    service = newService(TransactionOperations.withoutTransaction());

    WantedCrawlSummary summary = service.ingestAll();

    assertThat(summary.fetched()).isEqualTo(2);
    assertThat(summary.created()).isEqualTo(2);
    assertThat(client.pageFetches).isEqualTo(2);
  }

  @Test
  @DisplayName("첫 목록 페이지를 못 받으면 listFailed 로 기록한다")
  void firstListFailureFlagged() {
    client.failListAtOffset = 0;

    WantedCrawlSummary summary = service.ingestAll();

    assertThat(summary.pages()).isZero();
    assertThat(summary.listFailed()).isTrue();
    assertThat(summary.toDetails()).containsEntry("listFailed", true);
  }

  @Test
  @DisplayName("도중에 목록을 못 받으면 부분 결과를 남기고 listFailed 로 기록한다")
  void midRunListFailureKeepsPartial() {
    client.failListAtOffset = WantedClient.LIST_LIMIT;

    WantedCrawlSummary summary = service.ingestAll();

    assertThat(summary.pages()).isEqualTo(1);
    assertThat(summary.created()).isEqualTo(3);
    assertThat(summary.listFailed()).isTrue();
  }

  @Test
  @DisplayName("요약은 작업 details 맵으로 바뀐다")
  void summaryToDetails() {
    Map<String, Object> d =
        new WantedCrawlSummary(1, 3, 2, 1, 0, 4, Map.of("Go", 1), false).toDetails();
    assertThat(d)
        .containsEntry("errors", 4)
        .containsEntry("created", 2)
        .containsEntry("droppedSkills", Map.of("Go", 1));
  }

  private static JobPosting existing(String externalId) {
    return new JobPosting(
        "WANTED",
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
