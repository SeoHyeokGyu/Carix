package com.carix.jobposting.jumpit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.crawler.SaraminDetailFetcher;
import com.carix.jobposting.normalize.SkillTaxonomy;
import com.carix.jobposting.service.JobPostingIngestService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 클래스·메서드에 @Transactional 을 붙이지 않는다 — 운영처럼 바깥 트랜잭션 없이 지연 로딩 스킬 접근을 검증한다. */
@SpringBootTest
class JumpitIngestLazySkillsTest {

  private static final String EXTERNAL_ID = "99900001";

  private static final String LIST_PAGE =
      """
      {"result":{"positions":[{"id":99900001,"jobCategory":"서버/백엔드 개발자","title":"백엔드 엔지니어",
      "companyName":"테스트사","techStacks":["Java"],"newcomer":false,"minCareer":3,"maxCareer":7,
      "locations":["서울 중구"],"alwaysOpen":false,"closedAt":"2026-11-05T23:59:59"}],
      "emptyPosition":false}}
      """;

  private static final String EMPTY_PAGE = "{\"result\":{\"positions\":[],\"emptyPosition\":true}}";

  @Autowired private JobPostingRepository postings;
  @Autowired private RawIngestRepository rawIngests;
  @Autowired private JobPostingIngestService ingestService;
  @Autowired private SkillTaxonomy taxonomy;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    postings.deleteAll();
    rawIngests.deleteAll();
  }

  @Test
  @DisplayName("트랜잭션 없는 스레드에서 점핏 보강이 지연 로딩 스킬 컬렉션에 스킬을 더한다")
  void enrichesLazySkillsWithoutOuterTransaction() {
    var tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(
        s -> {
          Skill kafka = ingestService.upsertKnownSkill("Kafka");
          JobPosting posting =
              JobPosting.builder()
                  .source("SARAMIN")
                  .externalId(EXTERNAL_ID)
                  .title("백엔드 엔지니어")
                  .companyName("테스트사")
                  .jobFamily("DEVELOPER")
                  .firstSeenMonth("202610")
                  .lastSeenMonth("202610")
                  .collectedAt(Instant.now())
                  .build();
          posting.addSkill(kafka);
          posting.addCollectedVia(CollectedVia.SARAMIN_SEARCH);
          postings.save(posting);
        });

    JumpitClient client =
        new JumpitClient() {
          @Override
          public Optional<String> fetchListPage(int page) {
            return Optional.of(page == 1 ? LIST_PAGE : EMPTY_PAGE);
          }

          @Override
          public Optional<String> fetchDetail(String id) {
            return Optional.empty();
          }
        };
    SaraminDetailFetcher saramin = mock(SaraminDetailFetcher.class);
    given(saramin.fetch(anyString())).willReturn(Optional.empty());
    var service =
        new JumpitIngestService(
            client,
            new JumpitParser(),
            new JumpitFamilyResolver(),
            postings,
            rawIngests,
            ingestService,
            taxonomy,
            saramin,
            tx);

    JumpitIngestSummary summary = service.ingestAll();

    assertThat(summary.errors()).isZero();
    assertThat(summary.enriched()).isEqualTo(1);
    tx.executeWithoutResult(
        s -> {
          JobPosting reloaded =
              postings.findBySourceAndExternalId("SARAMIN", EXTERNAL_ID).orElseThrow();
          assertThat(reloaded.getSkills())
              .extracting(Skill::getStandardName)
              .contains("Kafka", "Java");
          assertThat(reloaded.getCollectedVia()).isEqualTo("SARAMIN_SEARCH,JUMPIT");
        });
  }
}
