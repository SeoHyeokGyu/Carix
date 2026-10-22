package com.carix.jobposting.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.normalize.PostingSalary;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SalaryReclassifyServiceTest {

  @Autowired private JobPostingRepository repository;

  private static final String BAND_HTML =
      "<meta name=\"description\" content=\"경력 : 경력 5년이상, 급여 : 회사 내규에 따름(면접 후 결정), 마감일 : 2026.09.30\"/>"
          + "<script>self.__next_f.push([1,\"{\\\"pay\\\":{\\\"payType\\\":\\\"ANNUALLY_SALARY\\\",\\\"payRange\\\":{\\\"from\\\":4000,\\\"to\\\":7001,\\\"fromInclusive\\\":true,\\\"toInclusive\\\":false}},\\\"benefitCodes\\\":[]}\"])</script>";
  private static final String PUBLIC_HTML =
      "<meta name=\"description\" content=\"경력 : 경력 5년이상, 급여 : 연봉 5,000만원, 마감일 : 2026.09.30\"/>";

  private void save(String source, String id, Long min, Long max) {
    repository.save(
        JobPosting.builder()
            .source(source)
            .externalId(id)
            .title("t")
            .jobFamily("DEVELOPER")
            .region("서울 강남구")
            .employmentType("FULL_TIME")
            .experienceMin(5)
            .salaryMin(min)
            .salaryMax(max)
            .collectedAt(Instant.now())
            .build());
  }

  @BeforeEach
  void seed() {
    save("JOBKOREA", "1", 40_000_000L, 70_000_000L); // 원문은 회사내규
    save("JOBKOREA", "2", null, null); // 원문은 공개 5,000만원
    save("JOBKOREA", "3", 30_000_000L, 40_000_000L); // 받지 못함
    repository.flush();
  }

  private SalaryReclassifyService service() {
    return new SalaryReclassifyService(
        repository,
        (source, id) ->
            switch (id) {
              case "1" -> Optional.of(BAND_HTML);
              case "2" -> Optional.of(PUBLIC_HTML);
              default -> Optional.empty();
            });
  }

  @Test
  @DisplayName("측정 모드는 판정 분포와 공개 연봉 동료 수만 내고 DB 를 바꾸지 않는다")
  void dryRun_writesNothing() {
    Map<String, Object> summary = service().run(true, 0L);

    assertThat(summary)
        .containsEntry("dryRun", true)
        .containsEntry("total", 3)
        .containsEntry("unreachable", 1);
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Integer>> peers =
        (Map<String, Map<String, Integer>>) summary.get("publicPeers");
    assertThat(peers.get("DEVELOPER/MID")).containsEntry("before", 2).containsEntry("after", 1);
    assertThat(repository.findBySourceAndExternalId("JOBKOREA", "1").orElseThrow().getSalaryMin())
        .isEqualTo(40_000_000L);
  }

  @Test
  @DisplayName("적용 모드는 판정을 저장하고 받지 못한 공고는 값을 비공개 구간으로 옮겨 UNKNOWN")
  void apply_unreachableKeepsValuesAsBand() {
    service().run(false, 0L);
    repository.flush();

    JobPosting band = repository.findBySourceAndExternalId("JOBKOREA", "1").orElseThrow();
    JobPosting pub = repository.findBySourceAndExternalId("JOBKOREA", "2").orElseThrow();
    JobPosting lost = repository.findBySourceAndExternalId("JOBKOREA", "3").orElseThrow();
    assertThat(band.getSalaryDisclosure()).isEqualTo("BAND");
    assertThat(band.getSalaryMin()).isNull();
    assertThat(band.getSalaryBandMin()).isEqualTo(40_000_000L);
    assertThat(pub.getSalaryDisclosure()).isEqualTo("PUBLIC");
    assertThat(pub.getSalaryMin()).isEqualTo(50_000_000L);
    assertThat(lost.getSalaryDisclosure()).isEqualTo("UNKNOWN");
    assertThat(lost.getSalaryBandMax()).isEqualTo(40_000_000L);
  }

  @Test
  @DisplayName("받았지만 판정 불가인 페이지(차단·캡차)는 받지 못함으로 다뤄 기존 값을 구간으로 보존한다")
  void apply_fetchedButUnknownTreatedAsUnreachable() {
    save("JOBKOREA", "4", 60_000_000L, 80_000_000L);
    repository.flush();
    SalaryReclassifyService service =
        new SalaryReclassifyService(
            repository,
            (source, id) ->
                "4".equals(id) ? Optional.of("<html>captcha</html>") : Optional.empty());

    Map<String, Object> summary = service.run(false, 0L);
    repository.flush();

    JobPosting blocked = repository.findBySourceAndExternalId("JOBKOREA", "4").orElseThrow();
    assertThat(blocked.getSalaryDisclosure()).isEqualTo("UNKNOWN");
    assertThat(blocked.getSalaryMin()).isNull();
    assertThat(blocked.getSalaryBandMin()).isEqualTo(60_000_000L);
    assertThat(summary).containsEntry("total", 4).containsEntry("unreachable", 4);
  }

  @Test
  @DisplayName("jobFamily 가 null 인 공고도 죽지 않고 센다")
  void nullJobFamilyDoesNotCrash() {
    repository.save(
        JobPosting.builder()
            .source("JOBKOREA")
            .externalId("7")
            .title("t")
            .region("서울 강남구")
            .employmentType("FULL_TIME")
            .collectedAt(Instant.now())
            .build());
    repository.flush();

    Map<String, Object> summary = service().run(true, 0L);

    assertThat(summary).containsEntry("total", 4).containsEntry("errors", 0);
  }

  @Test
  @DisplayName("가져오기가 예외를 던진 공고는 errors 로 세고 나머지는 계속 적용한다")
  void fetcherExceptionCountedAndOthersApplied() {
    SalaryReclassifyService service =
        new SalaryReclassifyService(
            repository,
            (source, id) -> {
              if ("1".equals(id)) {
                throw new IllegalStateException("boom");
              }
              return "2".equals(id) ? Optional.of(PUBLIC_HTML) : Optional.empty();
            });

    Map<String, Object> summary = service.run(false, 0L);
    repository.flush();

    assertThat(summary).containsEntry("errors", 1).containsEntry("total", 3);
    JobPosting failed = repository.findBySourceAndExternalId("JOBKOREA", "1").orElseThrow();
    assertThat(failed.getSalaryMin()).isEqualTo(40_000_000L);
    assertThat(failed.getSalaryDisclosure()).isEqualTo("UNKNOWN");
    JobPosting ok = repository.findBySourceAndExternalId("JOBKOREA", "2").orElseThrow();
    assertThat(ok.getSalaryDisclosure()).isEqualTo("PUBLIC");
  }

  @Test
  @DisplayName("판정 전 공고만 모드는 이미 판정된 공고를 열지도 세지도 않는다")
  void onlyUnjudged_skipsJudgedPostings() {
    JobPosting judged = repository.findBySourceAndExternalId("JOBKOREA", "1").orElseThrow();
    judged.applySalary(PostingSalary.publicRange(50_000_000L, 50_000_000L));
    repository.saveAndFlush(judged);
    java.util.List<String> fetched = new java.util.ArrayList<>();
    SalaryReclassifyService service =
        new SalaryReclassifyService(
            repository,
            (source, id) -> {
              fetched.add(id);
              return "2".equals(id) ? Optional.of(PUBLIC_HTML) : Optional.empty();
            });

    Map<String, Object> summary = service.run(false, 0L, true);
    repository.flush();

    assertThat(fetched).containsExactlyInAnyOrder("2", "3");
    assertThat(summary).containsEntry("total", 2).containsEntry("onlyUnjudged", true);
    assertThat(repository.findBySourceAndExternalId("JOBKOREA", "1").orElseThrow().getSalaryMin())
        .isEqualTo(50_000_000L);
    assertThat(
            repository
                .findBySourceAndExternalId("JOBKOREA", "2")
                .orElseThrow()
                .getSalaryDisclosure())
        .isEqualTo("PUBLIC");
  }
}
