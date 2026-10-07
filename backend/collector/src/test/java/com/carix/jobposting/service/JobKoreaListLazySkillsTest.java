package com.carix.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.normalize.ExperienceRange;
import com.carix.jobposting.seed.JobKoreaListingFields;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 클래스·메서드에 @Transactional 을 붙이지 않는다 — crawlAndIngestJobKorea 가 this 로 호출해 트랜잭션이 없는 상태를 재현한다. */
@SpringBootTest
class JobKoreaListLazySkillsTest {

  private static final String EXTERNAL_ID = "99900003";

  @Autowired private JobPostingRepository postings;
  @Autowired private JobPostingIngestService service;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    postings.deleteAll();
  }

  private void seed(String via) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            s -> {
              JobPosting posting =
                  JobPosting.builder()
                      .source("JOBKOREA")
                      .externalId(EXTERNAL_ID)
                      .title("백엔드 엔지니어")
                      .companyName("테스트사")
                      .jobFamily("DEVELOPER")
                      .firstSeenMonth("202610")
                      .lastSeenMonth("202610")
                      .collectedAt(Instant.now())
                      .build();
              posting.addSkill(service.upsertKnownSkill("Kafka"));
              posting.addCollectedVia(via);
              postings.save(posting);
            });
  }

  private void recrawl() {
    var fields = new JobKoreaListingFields("정규직", null, null, null, null, null);
    service.ingestJobKoreaPosting(
        EXTERNAL_ID,
        "백엔드 엔지니어 Java",
        "테스트사",
        "서울",
        ExperienceRange.parse("경력 3년"),
        "오늘",
        fields,
        null,
        Instant.now(),
        "202610");
  }

  private List<String> skillNames() {
    return new TransactionTemplate(transactionManager)
        .execute(
            s ->
                postings
                    .findBySourceAndExternalId("JOBKOREA", EXTERNAL_ID)
                    .orElseThrow()
                    .getSkills()
                    .stream()
                    .map(Skill::getStandardName)
                    .toList());
  }

  @Test
  @DisplayName("트랜잭션 없이 기존 잡코리아 공고를 목록 재수집하면 스킬이 다시 매겨진다")
  void listRecrawlReplacesSkills() {
    seed(CollectedVia.JOBKOREA_LIST);

    assertThatCode(this::recrawl).doesNotThrowAnyException();

    assertThat(skillNames()).contains("Java").doesNotContain("Kafka");
  }

  @Test
  @DisplayName("트랜잭션 없이 호출해도 상세 재분류와 전체 스킬 재정제는 프록시 트랜잭션 안에서 돈다")
  void proxiedEntryPointsRunInTransaction() {
    seed(CollectedVia.JOBKOREA_LIST);

    assertThatCode(() -> service.refreshJobKoreaFromDetail(EXTERNAL_ID, "<html></html>"))
        .doesNotThrowAnyException();
    assertThatCode(service::refineAllPostingSkills).doesNotThrowAnyException();
  }
}
