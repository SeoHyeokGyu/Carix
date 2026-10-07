package com.carix.jobposting.wanted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.backfill.BackfillSampler;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import com.carix.pipeline.dto.BackfillRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

class WantedBackfillServiceTest {

  private static final String ACTIVE_DATE = "2026-10-07";
  private static final String SEPT = "2026-09-15";
  private static final String OCT = "2026-10-15";

  private JobPostingRepository postings;
  private SkillTaxonomy taxonomy;
  private FakeClient client;
  private WantedBackfillService service;
  private String active;
  private String closed;

  static final class FakeClient implements WantedClient {
    final Map<Long, String> pages = new HashMap<>();
    final Map<Long, String> details = new HashMap<>();
    final List<Long> pageFetches = new ArrayList<>();
    final List<Long> detailFetches = new ArrayList<>();
    final List<Integer> listFetches = new ArrayList<>();
    Optional<String> list = Optional.empty();

    @Override
    public Optional<String> fetchList(int offset) {
      listFetches.add(offset);
      return list;
    }

    @Override
    public Optional<String> fetchPage(long id) {
      pageFetches.add(id);
      return Optional.ofNullable(pages.get(id));
    }

    @Override
    public Optional<String> fetchDetailApi(long id) {
      detailFetches.add(id);
      return Optional.ofNullable(details.get(id));
    }
  }

  private static String fixture(String name) throws IOException {
    try (var in = WantedBackfillServiceTest.class.getResourceAsStream("/wanted/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Skill skill(String name) {
    Skill s = new Skill(name, "SKILL", false);
    ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
    return s;
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

  private String devPage(long id, String date) {
    return active.replace("391003", String.valueOf(id)).replace(ACTIVE_DATE, date);
  }

  private String nonDevPage(long id, String date) {
    return closed.replace("315000", String.valueOf(id)).replace("2025-11-17", date);
  }

  @BeforeEach
  void setUp() throws IOException {
    active = fixture("page-active.html");
    closed = fixture("page-closed.html");
    postings = mock(JobPostingRepository.class);
    PostingSkillService skills = mock(PostingSkillService.class);
    taxonomy = mock(SkillTaxonomy.class);
    given(postings.save(any(JobPosting.class))).willAnswer(i -> i.getArgument(0));
    given(postings.findBySourceAndExternalId(anyString(), anyString()))
        .willReturn(Optional.empty());
    given(taxonomy.matchKnownSkills(anyString())).willReturn(List.of());
    given(taxonomy.matchKnownSkills("Kotlin")).willReturn(List.of("Kotlin"));
    given(taxonomy.matchKnownSkills("AWS")).willReturn(List.of("AWS"));
    given(taxonomy.extractSkillsFromSentence(anyString())).willReturn(List.of());
    given(skills.upsertKnownSkill(anyString())).willAnswer(i -> skill(i.getArgument(0)));
    client = new FakeClient();
    service =
        new WantedBackfillService(
            delay -> client,
            new WantedParser(),
            postings,
            skills,
            taxonomy,
            TransactionOperations.withoutTransaction());
  }

  private static BackfillRequest request(long minId, long maxId, String from, String to) {
    return new BackfillRequest(from, to, 250, 2_000, 0L, minId, maxId, "WANTED");
  }

  /** 1000~1009 한 달치: 개발 저장, 비개발, 다른 달, 404, __NEXT_DATA__ 없음, 기존 표본, 기존 정기 수집. */
  private void seedSeptember() throws IOException {
    String detail = fixture("detail-api.json");
    for (long id : List.of(1000L, 1001L, 1002L)) {
      client.pages.put(id, devPage(id, SEPT));
      client.details.put(id, detail);
    }
    client.pages.put(1003L, nonDevPage(1003, SEPT));
    client.pages.put(1004L, devPage(1004, OCT));
    client.pages.put(1006L, fixture("page-nodata.html"));
    client.pages.put(1007L, devPage(1007, SEPT));
    client.pages.put(1008L, devPage(1008, SEPT));
    client.details.put(1008L, detail);
    client.pages.put(1009L, devPage(1009, SEPT));
    client.details.put(1009L, "not json");
    JobPosting sampled = existing("1007");
    sampled.markSampled("202609");
    given(postings.findBySourceAndExternalId("WANTED", "1007")).willReturn(Optional.of(sampled));
    given(postings.findBySourceAndExternalId("WANTED", "1008"))
        .willReturn(Optional.of(existing("1008")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Integer> dropped(Map<String, Object> summary) {
    return (Map<String, Integer>) summary.get("droppedSkills");
  }

  @Test
  @DisplayName("개발 공고만 저장하고 나머지는 사유별로 센다")
  void sortsCandidatesByOutcome() throws IOException {
    seedSeptember();

    Map<String, Object> summary = service.run(request(1000, 1009, "202609", "202609"));

    BackfillSampler.Result r = (BackfillSampler.Result) summary.get("202609");
    assertThat(r.saved()).isEqualTo(5);
    assertThat(r.nonTarget()).isEqualTo(1);
    assertThat(r.otherMonth()).isEqualTo(1);
    assertThat(r.empty()).isEqualTo(2);
    assertThat(r.alreadySampled()).isEqualTo(1);
    assertThat(r.errors()).isZero();
  }

  @Test
  @DisplayName("정기 수집으로 이미 있는 ID 는 새 행 없이 표본으로 표시한다")
  void marksExistingListedPostingAsSampled() throws IOException {
    seedSeptember();
    JobPosting listed = existing("1008");
    given(postings.findBySourceAndExternalId("WANTED", "1008")).willReturn(Optional.of(listed));

    service.run(request(1000, 1009, "202609", "202609"));

    assertThat(listed.getSampledMonth()).isEqualTo("202609");
    assertThat(listed.getCollectedVia()).contains("WANTED_BACKFILL");
    verify(postings, never()).save(argThat(p -> p != listed && "1008".equals(p.getExternalId())));
  }

  @Test
  @DisplayName("상세 API 는 개발 직군이고 그 달인 후보에만 부르고 못 읽으면 apiMisses 로 센다")
  void detailApiOnlyForSavedCandidates() throws IOException {
    seedSeptember();

    Map<String, Object> summary = service.run(request(1000, 1009, "202609", "202609"));

    assertThat(client.detailFetches).containsExactlyInAnyOrder(1000L, 1001L, 1002L, 1009L);
    assertThat(summary.get("apiMisses")).isEqualTo(1);
    assertThat(dropped(summary)).containsEntry("Node.js", 3);
  }

  @Test
  @DisplayName("범위 탐색에서 연 페이지는 표본기가 다시 요청하지 않는다")
  void pagesAreFetchedOnce() throws IOException {
    seedSeptember();

    service.run(request(1000, 1009, "202609", "202609"));

    assertThat(client.pageFetches).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("한 건 저장이 실패해도 달 전체를 버리지 않고 실패 공고의 미등록 스킬은 총계에 넣지 않는다")
  void saveFailureIsIsolated() throws IOException {
    seedSeptember();
    given(postings.save(any(JobPosting.class)))
        .willAnswer(
            i -> {
              JobPosting p = i.getArgument(0);
              if ("1001".equals(p.getExternalId())) {
                throw new IllegalStateException("db down");
              }
              return p;
            });

    Map<String, Object> summary = service.run(request(1000, 1009, "202609", "202609"));

    BackfillSampler.Result r = (BackfillSampler.Result) summary.get("202609");
    assertThat(r.errors()).isEqualTo(1);
    assertThat(r.saved()).isEqualTo(4);
    assertThat(dropped(summary)).containsEntry("Node.js", 2);
  }

  @Test
  @DisplayName("요청한 ID 와 페이지의 ID 가 다르면 빈 페이지로 센다")
  void idMismatchIsEmpty() throws IOException {
    seedSeptember();
    client.pages.put(1002L, devPage(1500, SEPT));

    Map<String, Object> summary = service.run(request(1000, 1009, "202609", "202609"));

    BackfillSampler.Result r = (BackfillSampler.Result) summary.get("202609");
    assertThat(r.empty()).isEqualTo(3);
    assertThat(r.saved()).isEqualTo(4);
  }

  @Test
  @DisplayName("달이 끝나면 다음 달 시작 ID 보다 작은 페이지 캐시를 비운다")
  void evictsPageCacheAfterEachMonth() throws IOException {
    seedSeptember();
    for (long id = 1010; id <= 1019; id++) {
      client.pages.put(id, devPage(id, OCT));
    }
    List<Long> nextStarts = new ArrayList<>();
    List<Integer> staleCounts = new ArrayList<>();

    service.run(
        request(1000, 1019, "202609", "202610"),
        (nextStartId, cachedIds) -> {
          nextStarts.add(nextStartId);
          staleCounts.add((int) cachedIds.stream().filter(id -> id < nextStartId).count());
        });

    assertThat(nextStarts).hasSize(2);
    assertThat(nextStarts.get(0)).isGreaterThan(1000L);
    assertThat(staleCounts).containsOnly(0);
  }

  @Test
  @DisplayName("시작 월이 끝 월보다 늦으면 거절한다")
  void rejectsReversedMonths() {
    assertThatThrownBy(() -> service.run(request(1000, 1009, "202610", "202609")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static BackfillRequest requestWithoutMaxId(long minId, String from, String to) {
    return new BackfillRequest(from, to, 250, 2_000, 0L, minId, null, "WANTED");
  }

  @Test
  @DisplayName("maxId 가 없으면 목록 API 의 최신 공고 번호까지만 탐색한다")
  void derivesUpperBoundFromNewestListedPosting() throws IOException {
    seedSeptember();
    client.list =
        Optional.of("{\"data\":[{\"id\":1005},{\"id\":1009},{\"id\":1002}],\"links\":{}}");

    Map<String, Object> summary = service.run(requestWithoutMaxId(1000, "202609", "202609"));

    assertThat(client.listFetches).containsExactly(0);
    assertThat(client.pageFetches).isNotEmpty().allMatch(id -> id <= 1009L);
    assertThat(((BackfillSampler.Result) summary.get("202609")).saved()).isEqualTo(5);
  }

  @Test
  @DisplayName("요청의 maxId 는 목록 API 를 부르지 않고 그대로 쓴다")
  void explicitMaxIdSkipsListLookup() throws IOException {
    seedSeptember();

    service.run(request(1000, 1009, "202609", "202609"));

    assertThat(client.listFetches).isEmpty();
  }

  @Test
  @DisplayName("목록 API 를 못 읽으면 기본 상한으로 돌아가 그대로 실행한다")
  void fallsBackToDefaultUpperBoundWhenListFails() throws IOException {
    seedSeptember();
    client.list = Optional.empty();

    Map<String, Object> summary = service.run(requestWithoutMaxId(1000, "202609", "202609"));

    assertThat(client.pageFetches).anyMatch(id -> id > 1009L);
    assertThat(summary).containsKey("202609");
  }

  @Test
  @DisplayName("목록 응답을 해석하지 못해도 기본 상한으로 실행한다")
  void fallsBackToDefaultUpperBoundWhenListUnparseable() throws IOException {
    seedSeptember();
    client.list = Optional.of("not json");

    Map<String, Object> summary = service.run(requestWithoutMaxId(1000, "202609", "202609"));

    assertThat(client.pageFetches).anyMatch(id -> id > 1009L);
    assertThat(summary).containsKey("202609");
  }
}
