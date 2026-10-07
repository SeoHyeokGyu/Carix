package com.carix.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.normalize.ExperienceRange;
import com.carix.jobposting.seed.SaraminListingFields;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 클래스·메서드에 @Transactional 을 붙이지 않는다 — crawlAndIngestSaramin 처럼 호출 스레드에 트랜잭션이 없는 상태를 재현한다. */
@SpringBootTest
class SaraminRecrawlLazySkillsTest {

  private static final String EXTERNAL_ID = "99900002";

  @Autowired private JobPostingRepository postings;
  @Autowired private JobPostingIngestService service;
  @Autowired private PostingSkillService skillService;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    postings.deleteAll();
  }

  private void seed(String via) {
    seed(via, "DEVELOPER", null);
  }

  private void seed(String via, String family, Integer experienceMin) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            s -> {
              JobPosting posting =
                  JobPosting.builder()
                      .source("SARAMIN")
                      .externalId(EXTERNAL_ID)
                      .title("백엔드 엔지니어")
                      .companyName("테스트사")
                      .jobFamily(family)
                      .experienceMin(experienceMin)
                      .firstSeenMonth("202610")
                      .lastSeenMonth("202610")
                      .collectedAt(Instant.now())
                      .build();
              posting.addSkill(skillService.upsertKnownSkill("Kafka"));
              posting.addCollectedVia(via);
              postings.save(posting);
            });
  }

  private void recrawl() {
    var fields = new SaraminListingFields("경력 3년↑", "정규직", null, List.of("Java"), List.of());
    service.ingestSaraminPosting(
        EXTERNAL_ID,
        "백엔드 엔지니어",
        "테스트사",
        "서울",
        ExperienceRange.parse("경력 3년↑"),
        null,
        fields,
        Instant.now(),
        "202610",
        null);
  }

  private JobPosting loaded() {
    return new TransactionTemplate(transactionManager)
        .execute(s -> postings.findBySourceAndExternalId("SARAMIN", EXTERNAL_ID).orElseThrow());
  }

  private List<String> skillNames() {
    return new TransactionTemplate(transactionManager)
        .execute(
            s ->
                postings
                    .findBySourceAndExternalId("SARAMIN", EXTERNAL_ID)
                    .orElseThrow()
                    .getSkills()
                    .stream()
                    .map(Skill::getStandardName)
                    .toList());
  }

  @Test
  @DisplayName("트랜잭션 없이 기존 사람인 공고를 재수집하면 스킬이 목록 태그로 바뀐다")
  void recrawlReplacesSkills() {
    seed(CollectedVia.SARAMIN_SEARCH);

    assertThatCode(this::recrawl).doesNotThrowAnyException();

    assertThat(skillNames()).contains("Java").doesNotContain("Kafka");
  }

  @Test
  @DisplayName("점핏 이력이 있는 기존 공고를 재수집하면 기존 스킬을 지우지 않고 더한다")
  void recrawlKeepsSkillsOfJumpitRows() {
    seed(CollectedVia.JUMPIT);

    assertThatCode(this::recrawl).doesNotThrowAnyException();

    assertThat(skillNames()).contains("Java", "Kafka");
  }

  @Test
  @DisplayName("점핏 이력이 있는 기존 공고의 직무군과 경력은 사람인 재수집이 덮어쓰지 않는다")
  void recrawlKeepsFamilyAndExperienceOfJumpitRows() {
    seed(CollectedVia.JUMPIT, "DATA", 5);

    assertThatCode(this::recrawl).doesNotThrowAnyException();

    JobPosting posting = loaded();
    assertThat(posting.getJobFamily()).isEqualTo("DATA");
    assertThat(posting.getExperienceMin()).isEqualTo(5);
  }
}
