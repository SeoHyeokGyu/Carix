package com.carix.pipeline.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.carix.pipeline.dto.explore.PostingPage;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@Transactional
class PostingsExploreTest {

  @Autowired private JobPostingRepository repository;
  @Autowired private SkillRepository skillRepository;
  @Autowired private DataExploreService service;

  private JobPosting posting(
      String source, String id, String company, String seen, String sampled) {
    return repository.save(
        JobPosting.builder()
            .source(source)
            .externalId(id)
            .title("공고 " + id)
            .companyName(company)
            .jobFamily("DEVELOPER")
            .salaryMin(50_000_000L)
            .firstSeenMonth(seen)
            .lastSeenMonth(seen)
            .sampledMonth(sampled)
            .collectedAt(Instant.now())
            .build());
  }

  @BeforeEach
  void seed() {
    Skill java = skillRepository.save(new Skill("Java", "LANGUAGE", false));
    JobPosting a = posting("JOBKOREA", "49500742", "(주)평화이즈", "202609", "202609");
    a.addSkill(java);
    repository.save(a);
    posting("JOBKOREA", "49500800", "100%_회사", "202609", null);
    posting("SARAMIN", "51000001", "사람인회사", "202609", null);
    repository.flush();
  }

  @Test
  @DisplayName("표본 필터와 원천 링크, 스킬 목록, 제시 연봉 범위")
  void postings_sampled() {
    PostingPage page = service.postings("202609", null, true, null, null, null, null, null);

    assertThat(page.total()).isEqualTo(1);
    PostingPage.Item item = page.items().getFirst();
    assertThat(item.url()).isEqualTo("https://www.jobkorea.co.kr/Recruit/GI_Read/49500742");
    assertThat(item.skills()).containsExactly("Java");
    assertThat(item.salaryMin()).isEqualTo(50_000_000L);
    assertThat(item.salaryMax()).isNull();
  }

  @Test
  @DisplayName("정기 수집 필터는 sampled_month 가 비고 관측월이 그 달인 공고, 사람인 링크 형식")
  void postings_crawled() {
    PostingPage page = service.postings("202609", "SARAMIN", false, null, null, null, null, null);
    assertThat(page.items())
        .extracting(PostingPage.Item::url)
        .containsExactly("https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=51000001");
  }

  @Test
  @DisplayName("OTHER 직무 필터는 직무가 비었거나 알 수 없는 공고까지 센다")
  void postings_otherIncludesNullFamily() {
    repository.saveAndFlush(
        JobPosting.builder()
            .source("SARAMIN")
            .externalId("51000002")
            .title("공고 51000002")
            .companyName("무직무회사")
            .firstSeenMonth("202609")
            .lastSeenMonth("202609")
            .collectedAt(Instant.now())
            .build());

    PostingPage other = service.postings("202609", null, null, "OTHER", null, null, null, null);
    assertThat(other.items()).extracting(PostingPage.Item::externalId).containsExactly("51000002");
    assertThat(service.postings("202609", null, null, "DEVELOPER", null, null, null, null).total())
        .isEqualTo(3);
  }

  @Test
  @DisplayName("회사 필터의 % _ 는 글자 그대로 찾는다")
  void postings_companyFilterEscapesWildcards() {
    assertThat(service.postings("202609", null, null, null, "100%_", null, null, null).total())
        .isEqualTo(1);
    assertThat(service.postings("202609", null, null, null, "%", null, null, null).total())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("스킬 필터는 정확 일치, 스킬 없는 공고는 빈 목록")
  void postings_skillsEmptyList() {
    assertThat(service.postings("202609", null, null, null, null, "Java", null, null).total())
        .isEqualTo(1);
    PostingPage none = service.postings("202609", "SARAMIN", null, null, null, null, null, null);
    assertThat(none.items().getFirst().skills()).isEmpty();
  }

  @Test
  @DisplayName("결과 없음은 빈 목록, size 는 100 으로 자르고 month 없으면 400")
  void postings_emptyResult() {
    PostingPage empty = service.postings("202001", null, null, null, null, null, null, 500);
    assertThat(empty.total()).isZero();
    assertThat(empty.items()).isEmpty();
    assertThat(empty.size()).isEqualTo(100);
    assertThatThrownBy(() -> service.postings(null, null, null, null, null, null, null, null))
        .isInstanceOf(ResponseStatusException.class);
  }
}
