package com.carix.jobposting.wanted;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.OwnRowsCleaner;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.PostingSkillService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 클래스·메서드에 @Transactional 을 붙이지 않는다 — 운영처럼 바깥 트랜잭션 없이 지연 로딩 스킬 접근을 검증한다. */
@SpringBootTest
class WantedIngestLazyTest {

  private static final String EXTERNAL_ID = "99900002";

  private static final String LIST_PAGE =
      """
      {"links":{"next":null},"data":[{"id":99900002,"position":"백엔드 엔지니어",
      "company":{"name":"테스트사"},"category_tags":[{"id":872}]}]}
      """;

  @Autowired private JobPostingRepository postings;
  @Autowired private RawIngestRepository rawIngests;
  @Autowired private PostingSkillService skillService;
  @Autowired private SkillTaxonomy taxonomy;
  @Autowired private SkillRepository skillRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  private OwnRowsCleaner cleaner;

  @BeforeEach
  void snapshot() {
    cleaner = new OwnRowsCleaner(postings, skillRepository, rawIngests, transactionManager);
    cleaner.snapshot();
  }

  @AfterEach
  void cleanUp() {
    cleaner.cleanUp("WANTED", EXTERNAL_ID);
  }

  private WantedPosting posting() {
    return new WantedPosting(
        EXTERNAL_ID,
        "테스트사",
        "백엔드 엔지니어",
        LocalDate.of(2026, 10, 7),
        null,
        518,
        List.of(872),
        List.of("서버 개발자"),
        3,
        7,
        false,
        "서울 중구",
        "UNKNOWN",
        "Kafka 경험");
  }

  @Test
  @DisplayName("트랜잭션 없는 스레드에서 정기 수집이 기존 행을 건드려도, 이어진 표본 저장이 예외 없이 표본 월을 달고 스킬을 유지한다")
  void listedThenSampledWithoutOuterTransaction() {
    var tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(
        s -> {
          Skill kafka = skillService.upsertKnownSkill("Kafka");
          JobPosting posting =
              JobPosting.builder()
                  .source("WANTED")
                  .externalId(EXTERNAL_ID)
                  .title("백엔드 엔지니어")
                  .companyName("테스트사")
                  .jobFamily("DEVELOPER")
                  .firstSeenMonth("202609")
                  .lastSeenMonth("202609")
                  .collectedAt(Instant.now())
                  .build();
          posting.addSkill(kafka);
          posting.addCollectedVia(CollectedVia.WANTED_LIST);
          postings.save(posting);
        });
    WantedClient client =
        new WantedClient() {
          @Override
          public Optional<String> fetchList(int offset) {
            return Optional.of(offset == 0 ? LIST_PAGE : "{\"links\":{\"next\":null},\"data\":[]}");
          }

          @Override
          public Optional<String> fetchPage(long id) {
            throw new AssertionError("있는 공고는 페이지를 열지 않는다");
          }

          @Override
          public Optional<String> fetchDetailApi(long id) {
            throw new AssertionError("있는 공고는 상세 API 를 열지 않는다");
          }
        };
    var service =
        new WantedIngestService(
            client, new WantedParser(), postings, rawIngests, skillService, taxonomy, tx);

    WantedCrawlSummary summary = service.ingestAll();

    assertThat(summary.errors()).isZero();
    assertThat(summary.seen()).isEqualTo(1);

    var writer = new WantedPostingWriter(postings, skillService, taxonomy);
    Map<String, Integer> dropped = new TreeMap<>();
    Boolean created =
        tx.execute(
            s ->
                writer.upsertSampled(
                    posting(), List.of("Kotlin"), YearMonth.of(2026, 10), dropped));
    assertThat(created).isFalse();

    tx.executeWithoutResult(
        s -> {
          JobPosting reloaded =
              postings.findBySourceAndExternalId("WANTED", EXTERNAL_ID).orElseThrow();
          assertThat(reloaded.getSampledMonth()).isEqualTo("202610");
          assertThat(reloaded.getSkills()).extracting(Skill::getStandardName).contains("Kafka");
          assertThat(reloaded.getCollectedVia()).isEqualTo("WANTED_LIST,WANTED_BACKFILL");
        });
  }
}
