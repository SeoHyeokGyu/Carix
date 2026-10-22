package com.carix.pipeline.explore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.normalize.PostingSalary;
import com.carix.jobposting.normalize.SalaryRange;
import com.carix.pipeline.dto.explore.PostingPage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
  @Autowired private ExploreQueries queries;
  @Autowired private com.carix.market.MarketAggregateService aggregate;

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
    PostingPage page =
        service.postings("202609", null, true, null, null, null, null, null, null, null);

    assertThat(page.total()).isEqualTo(1);
    PostingPage.Item item = page.items().getFirst();
    assertThat(item.url()).isEqualTo("https://www.jobkorea.co.kr/Recruit/GI_Read/49500742");
    assertThat(item.skills()).containsExactly("Java");
    assertThat(item.salaryMin()).isEqualTo(50_000_000L);
    assertThat(item.salaryMax()).isNull();
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

    PostingPage other =
        service.postings("202609", null, null, "OTHER", null, null, null, null, null, null);
    assertThat(other.items()).extracting(PostingPage.Item::externalId).containsExactly("51000002");
    assertThat(
            service
                .postings("202609", null, null, "DEVELOPER", null, null, null, null, null, null)
                .total())
        .isEqualTo(3);
  }

  @Test
  @DisplayName("회사 필터의 % _ 는 글자 그대로 찾는다")
  void postings_companyFilterEscapesWildcards() {
    assertThat(
            service
                .postings("202609", null, null, null, "100%_", null, null, null, null, null)
                .total())
        .isEqualTo(1);
    assertThat(
            service.postings("202609", null, null, null, "%", null, null, null, null, null).total())
        .isEqualTo(1);
  }

  private DataExploreService at(String instant) {
    Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    return new DataExploreService(queries, repository, aggregate, clock);
  }

  @Test
  @DisplayName("month 를 안 주면 가장 최근 수집 월, 이번 달이면 current")
  void postings_defaultsToLatestMonth() {
    posting("SARAMIN", "51000100", "정기회사", "202610", null);
    repository.flush();
    DataExploreService fixed = at("2026-10-09T00:00:00Z");

    PostingPage page = fixed.postings(null, null, null, null, null, null, null, null, null, null);

    assertThat(page.month()).isEqualTo("202610");
    assertThat(page.current()).isTrue();
    assertThat(ids(page)).containsExactly("51000100");
  }

  @Test
  @DisplayName("최근 월은 표본월도 센다, 다른 필터와 무관")
  void postings_latestMonthIgnoresFilters() {
    posting("JOBKOREA", "49500999", "표본회사", "202608", "202610");
    repository.flush();

    PostingPage page =
        at("2026-10-09T00:00:00Z")
            .postings(null, "SARAMIN", null, null, null, null, null, null, null, null);

    assertThat(page.month()).isEqualTo("202610");
    assertThat(page.total()).isZero();
  }

  @Test
  @DisplayName("빈 month 는 없는 것으로, 형식이 틀리면 400")
  void postings_blankAndMalformedMonth() {
    PostingPage page = service.postings(" ", null, null, null, null, null, null, null, null, null);
    assertThat(page.month()).isEqualTo("202609");
    assertThatThrownBy(
            () -> service.postings("2026", null, null, null, null, null, null, null, null, null))
        .isInstanceOf(ResponseStatusException.class);
  }

  @Test
  @DisplayName("공개 구분 필터와 비공개 구간 필드")
  void postings_disclosureFilter() {
    JobPosting band = posting("JOBKOREA", "49500900", "내규회사", "202609", null);
    band.applySalary(PostingSalary.band(new SalaryRange(25_900_000L, 100_000_000L)));
    repository.saveAndFlush(band);

    PostingPage page =
        service.postings("202609", null, null, null, null, null, "BAND", null, null, null);

    assertThat(page.total()).isEqualTo(1);
    PostingPage.Item item = page.items().getFirst();
    assertThat(item.salaryMin()).isNull();
    assertThat(item.salaryBandMin()).isEqualTo(25_900_000L);
    assertThat(item.salaryDisclosure()).isEqualTo("BAND");
  }

  @Test
  @DisplayName("확인 경로와 원천 직군 태그를 내려 준다")
  void postings_collectedViaAndSourceTags() {
    JobPosting p = posting("SARAMIN", "51000009", "점핏회사", "202609", null);
    p.addCollectedVia("SARAMIN_SEARCH");
    p.addCollectedVia("JUMPIT");
    p.recordSourceJobTags("서버/백엔드 개발자");
    repository.saveAndFlush(p);

    PostingPage page =
        service.postings("202609", "SARAMIN", false, null, "점핏", null, null, null, null, null);

    PostingPage.Item item = page.items().getFirst();
    assertThat(item.collectedVia()).isEqualTo("SARAMIN_SEARCH,JUMPIT");
    assertThat(item.sourceJobTags()).isEqualTo("서버/백엔드 개발자");
  }

  private void collectedAt(String source, String id, String month, Instant at) {
    repository.saveAndFlush(
        JobPosting.builder()
            .source(source)
            .externalId(id)
            .title("공고 " + id)
            .companyName("정렬회사")
            .firstSeenMonth(month)
            .lastSeenMonth(month)
            .collectedAt(at)
            .build());
  }

  private List<String> ids(PostingPage page) {
    return page.items().stream().map(PostingPage.Item::externalId).toList();
  }

  @Test
  @DisplayName("알 수 없는 정렬은 400")
  void postings_invalidSort() {
    assertThatThrownBy(
            () ->
                service.postings(
                    "202609", null, null, null, null, null, null, "id; DROP", null, null))
        .isInstanceOf(ResponseStatusException.class);
  }
}
