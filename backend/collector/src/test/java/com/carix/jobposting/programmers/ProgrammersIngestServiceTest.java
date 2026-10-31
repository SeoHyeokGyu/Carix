package com.carix.jobposting.programmers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionOperations;

class ProgrammersIngestServiceTest {

  private JobPostingRepository postings;
  private RawIngestRepository rawIngests;
  private PostingSkillService skills;
  private SkillTaxonomy taxonomy;
  private FakeProgrammersClient client;
  private ProgrammersIngestService service;

  static final class FakeProgrammersClient implements ProgrammersClient {
    String page1Json;
    boolean failAtPage1 = false;
    java.util.Map<Integer, String> pages = new java.util.HashMap<>();

    FakeProgrammersClient(String page1Json) {
      this.page1Json = page1Json;
    }

    @Override
    public Optional<String> fetchListPage(int page) {
      if (failAtPage1 && page == 1) {
        return Optional.empty();
      }
      if (pages.containsKey(page)) {
        return Optional.ofNullable(pages.get(page));
      }
      if (page == 1) {
        return Optional.ofNullable(page1Json);
      }
      return Optional.of("{\"total_count\":2,\"total_pages\":1,\"job_positions\":[]}");
    }

    @Override
    public Optional<String> fetchDetail(String id) {
      return Optional.empty();
    }
  }

  @BeforeEach
  void setUp() {
    postings = mock(JobPostingRepository.class);
    rawIngests = mock(RawIngestRepository.class);
    skills = mock(PostingSkillService.class);
    taxonomy = mock(SkillTaxonomy.class);

    given(taxonomy.matchKnownSkills("Java")).willReturn(List.of("Java"));
    given(taxonomy.matchKnownSkills("React")).willReturn(List.of("React"));
    given(skills.upsertKnownSkill("Java")).willReturn(new Skill("Java", "Java", false));
    given(skills.upsertKnownSkill("React")).willReturn(new Skill("React", "React", false));

    String json =
        """
        {
          "total_count": 2,
          "total_pages": 1,
          "job_positions": [
            {
              "id": 101,
              "title": "백엔드 개발자",
              "company": {"name": "테크원"},
              "address": "서울 강남구",
              "min_career": 2,
              "max_career": 5,
              "position_tags": ["Java"],
              "start_at": "2026-03-01",
              "end_at": "2026-10-31",
              "active": true
            },
            {
              "id": 102,
              "title": "프론트엔드 개발자",
              "company": {"name": "테크투"},
              "address": "경기도 성남시",
              "min_career": 0,
              "max_career": 3,
              "position_tags": ["React"],
              "start_at": "2026-03-01",
              "end_at": "2026-10-31",
              "active": true
            }
          ]
        }
        """;

    client = new FakeProgrammersClient(json);
    service =
        new ProgrammersIngestService(
            client,
            new ProgrammersParser(),
            postings,
            rawIngests,
            skills,
            taxonomy,
            TransactionOperations.withoutTransaction());
  }

  @Test
  @DisplayName("신규 공고는 DB에 생성하고 이미 존재하는 공고는 관측월만 갱신한다")
  void ingestsNewAndExisting() {
    String currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

    // 101은 신규(없음), 102는 이미 있음
    given(postings.findBySourceAndExternalId("PROGRAMMERS", "101")).willReturn(Optional.empty());

    JobPosting existing102 =
        new JobPosting(
            "PROGRAMMERS",
            "102",
            "프론트엔드 개발자",
            "테크투",
            null,
            null,
            "경기도 성남시",
            0,
            3,
            "FULL_TIME",
            "UNKNOWN",
            null,
            null,
            "DEVELOPER",
            null,
            null,
            "202601",
            "202601",
            Instant.now());
    given(postings.findBySourceAndExternalId("PROGRAMMERS", "102"))
        .willReturn(Optional.of(existing102));

    ProgrammersCrawlSummary summary = service.ingestAll();

    assertThat(summary.pages()).isEqualTo(1);
    assertThat(summary.fetched()).isEqualTo(2);
    assertThat(summary.created()).isEqualTo(1);
    assertThat(summary.seen()).isEqualTo(1);
    assertThat(summary.errors()).isEqualTo(0);
    assertThat(summary.listFailed()).isFalse();

    // 101 신규 저장 및 102 갱신 저장 확인
    ArgumentCaptor<JobPosting> captor = ArgumentCaptor.forClass(JobPosting.class);
    verify(postings, times(2)).save(captor.capture());
    List<JobPosting> savedList = captor.getAllValues();

    JobPosting newPosting101 =
        savedList.stream().filter(p -> "101".equals(p.getExternalId())).findFirst().orElseThrow();
    assertThat(newPosting101.getSource()).isEqualTo("PROGRAMMERS");
    assertThat(newPosting101.getCollectedVia()).isEqualTo(CollectedVia.PROGRAMMERS);
    assertThat(newPosting101.getFirstSeenMonth()).isEqualTo(currentMonth);

    // 102 갱신 확인
    assertThat(existing102.getLastSeenMonth()).isEqualTo(currentMonth);
    assertThat(existing102.hasCollectedVia(CollectedVia.PROGRAMMERS)).isTrue();
  }

  @Test
  @DisplayName("목록 호출 실패 시 listFailed=true 로 끝난다")
  void failsWhenListFails() {
    client.failAtPage1 = true;
    ProgrammersCrawlSummary summary = service.ingestAll();
    assertThat(summary.listFailed()).isTrue();
    assertThat(summary.pages()).isEqualTo(0);
  }

  @Test
  @DisplayName("목록 JSON 파싱 실패 시 listFailed=true 로 안전하게 중단된다")
  void failsWhenParsingFails() {
    client.page1Json = "invalid-json";
    ProgrammersCrawlSummary summary = service.ingestAll();
    assertThat(summary.listFailed()).isTrue();
    assertThat(summary.pages()).isEqualTo(0);
  }

  @Test
  @DisplayName("동일한 공고 ID가 중복 전달될 경우 중복 등록을 방지한다")
  void handlesDuplicateIdsInResponse() {
    client.page1Json =
        """
        {
          "total_count": 2,
          "total_pages": 1,
          "job_positions": [
            {
              "id": 999,
              "title": "백엔드 개발자",
              "company": {"name": "테크원"}
            },
            {
              "id": 999,
              "title": "동일 ID 중복 공고",
              "company": {"name": "테크원"}
            }
          ]
        }
        """;

    ProgrammersCrawlSummary summary = service.ingestAll();
    assertThat(summary.fetched()).isEqualTo(2);
    assertThat(summary.created()).isEqualTo(1);
    verify(postings, times(1)).save(any());
  }

  @Test
  @DisplayName("공고 upsert 중 예외가 발생해도 다음 공고 처리를 계속하고 errors 가 증가한다")
  void handlesUpsertExceptionGracefully() {
    given(postings.save(any())).willThrow(new RuntimeException("DB Connection Fail"));

    ProgrammersCrawlSummary summary = service.ingestAll();
    assertThat(summary.errors()).isEqualTo(2);
    assertThat(summary.created()).isEqualTo(0);
  }

  @Test
  @DisplayName("RawIngest 저장 중 예외가 발생해도 수집 프로세스는 중단되지 않는다")
  void handlesRawIngestExceptionGracefully() {
    given(rawIngests.save(any())).willThrow(new RuntimeException("Raw ingest storage error"));

    ProgrammersCrawlSummary summary = service.ingestAll();
    assertThat(summary.pages()).isEqualTo(1);
    assertThat(summary.created()).isEqualTo(2);
    assertThat(summary.errors()).isEqualTo(0);
  }

  @Test
  @DisplayName("maxPages 파라미터가 유효하지 않으면 기본값을 사용하고, 양수면 해당 제한을 따른다")
  void handlesMaxPagesOverride() {
    // 1페이지당 1개씩 총 2페이지 데이터 준비
    client.page1Json =
        """
        {
          "total_count": 2,
          "total_pages": 2,
          "job_positions": [{"id": 1, "title": "P1", "company": {"name": "C1"}}]
        }
        """;
    client.pages.put(
        2,
        """
        {
          "total_count": 2,
          "total_pages": 2,
          "job_positions": [{"id": 2, "title": "P2", "company": {"name": "C2"}}]
        }
        """);

    // maxPagesOverride = 1 -> 1페이지만 수집
    ProgrammersCrawlSummary s1 = service.ingest(1);
    assertThat(s1.pages()).isEqualTo(1);
    assertThat(s1.fetched()).isEqualTo(1);

    // maxPagesOverride = null -> 전체(2페이지) 수집
    ProgrammersCrawlSummary s2 = service.ingest(null);
    assertThat(s2.pages()).isEqualTo(2);
    assertThat(s2.fetched()).isEqualTo(2);
  }
}
