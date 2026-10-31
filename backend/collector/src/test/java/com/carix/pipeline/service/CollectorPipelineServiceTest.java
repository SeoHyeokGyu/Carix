package com.carix.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.cpi.CpiIngestService;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.backfill.BackfillSampler;
import com.carix.jobposting.backfill.JobKoreaBackfillService;
import com.carix.jobposting.dedup.JobPostingDeduplicator;
import com.carix.jobposting.jumpit.JumpitIngestService;
import com.carix.jobposting.jumpit.JumpitIngestSummary;
import com.carix.jobposting.service.JobPostingIngestService;
import com.carix.jobposting.service.PostingSkillService;
import com.carix.jobposting.wanted.WantedIngestService;
import com.carix.market.MarketAggregateRepository;
import com.carix.market.MarketAggregateService;
import com.carix.pipeline.dto.BackfillRequest;
import com.carix.pipeline.dto.CrawlRequest;
import com.carix.pipeline.dto.JobExecutionResponse;
import com.carix.pipeline.dto.JobStatusResponse;
import com.carix.wagestat.WageStatIngestService;
import com.carix.wagestat.WageStatObservationRepository;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CollectorPipelineServiceTest {

  private JobPostingRepository jobPostingRepository;
  private RawIngestRepository rawIngestRepository;
  private SkillRepository skillRepository;
  private WageStatObservationRepository wageStatObservationRepository;
  private MarketAggregateRepository marketAggregateRepository;
  private JobPostingIngestService jobPostingIngestService;
  private PostingSkillService postingSkillService;
  private WageStatIngestService wageStatIngestService;
  private CpiIngestService cpiIngestService;
  private JobPostingDeduplicator jobPostingDeduplicator;
  private MarketAggregateService marketAggregateService;
  private JobKoreaBackfillService jobKoreaBackfillService;
  private JumpitIngestService jumpitIngestService;
  private WantedIngestService wantedIngestService;
  private com.carix.jobposting.wanted.WantedBackfillService wantedBackfillService;
  private com.carix.jobposting.programmers.ProgrammersIngestService programmersIngestService;

  private CollectorPipelineService service;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    rawIngestRepository = mock(RawIngestRepository.class);
    skillRepository = mock(SkillRepository.class);
    wageStatObservationRepository = mock(WageStatObservationRepository.class);
    marketAggregateRepository = mock(MarketAggregateRepository.class);
    jobPostingIngestService = mock(JobPostingIngestService.class);
    postingSkillService = mock(PostingSkillService.class);
    wageStatIngestService = mock(WageStatIngestService.class);
    cpiIngestService = mock(CpiIngestService.class);
    jobPostingDeduplicator = mock(JobPostingDeduplicator.class);
    marketAggregateService = mock(MarketAggregateService.class);
    jobKoreaBackfillService = mock(JobKoreaBackfillService.class);
    jumpitIngestService = mock(JumpitIngestService.class);
    wantedIngestService = mock(WantedIngestService.class);
    wantedBackfillService = mock(com.carix.jobposting.wanted.WantedBackfillService.class);
    programmersIngestService =
        mock(com.carix.jobposting.programmers.ProgrammersIngestService.class);

    service =
        new CollectorPipelineService(
            jobPostingRepository,
            rawIngestRepository,
            skillRepository,
            wageStatObservationRepository,
            marketAggregateRepository,
            jobPostingIngestService,
            postingSkillService,
            wageStatIngestService,
            cpiIngestService,
            jobPostingDeduplicator,
            marketAggregateService,
            jobKoreaBackfillService,
            mock(com.carix.jobposting.backfill.SalaryReclassifyService.class),
            jumpitIngestService,
            wantedIngestService,
            wantedBackfillService,
            programmersIngestService);
  }

  @Test
  @DisplayName("triggerCrawl ALL - 5대 포털(잡코리아, 사람인, 점핏, 원티드, 프로그래머스)을 비동기 병렬로 일괄 수집한다")
  void triggerCrawl_allPortalsParallelSuccess() throws Exception {
    given(jobPostingIngestService.crawlAndIngestJobKorea(any(), anyInt())).willReturn(10);
    given(jobPostingIngestService.crawlAndIngestSaramin(anyInt())).willReturn(15);
    given(jumpitIngestService.ingestAll())
        .willReturn(new JumpitIngestSummary(1, 15, 5, 10, 0, 0, Map.of(), false));
    given(wantedIngestService.ingestAll())
        .willReturn(
            new com.carix.jobposting.wanted.WantedCrawlSummary(
                1, 20, 8, 12, 0, 0, Map.of(), false));
    given(programmersIngestService.ingest(any()))
        .willReturn(
            new com.carix.jobposting.programmers.ProgrammersCrawlSummary(
                1, 20, 6, 14, 0, 0, Map.of(), false));

    JobExecutionResponse response =
        service.triggerCrawl(new CrawlRequest("ALL", null, 5, null, null, null));
    assertThat(response.status()).isEqualTo("ACCEPTED");

    TimeUnit.MILLISECONDS.sleep(300);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("portal", "ALL");
    assertThat(status.details()).containsEntry("successCount", 5);
    assertThat(status.details()).containsEntry("failedCount", 0);

    @SuppressWarnings("unchecked")
    Map<String, Object> portals = (Map<String, Object>) status.details().get("portals");
    assertThat(portals).containsOnlyKeys("JOBKOREA", "SARAMIN", "JUMPIT", "WANTED", "PROGRAMMERS");

    assertThat(status.details()).containsEntry("totalCreated", 10 + 15 + 5 + 8 + 6);
  }

  @Test
  @DisplayName("triggerCrawl ALL - 일부 포털 실패 시 결함 격리되어 나머지 포털은 정상 수집된다")
  void triggerCrawl_allPortalsPartialFailure() throws Exception {
    given(jobPostingIngestService.crawlAndIngestJobKorea(any(), anyInt())).willReturn(10);
    given(jobPostingIngestService.crawlAndIngestSaramin(anyInt())).willReturn(15);
    given(jumpitIngestService.ingestAll()).willThrow(new RuntimeException("점핏 서버 연결 시간 초과"));
    given(wantedIngestService.ingestAll())
        .willReturn(
            new com.carix.jobposting.wanted.WantedCrawlSummary(
                1, 20, 8, 12, 0, 0, Map.of(), false));
    given(programmersIngestService.ingest(any()))
        .willReturn(
            new com.carix.jobposting.programmers.ProgrammersCrawlSummary(
                1, 20, 6, 14, 0, 0, Map.of(), false));

    JobExecutionResponse response =
        service.triggerCrawl(new CrawlRequest("ALL", null, 5, null, null, null));
    assertThat(response.status()).isEqualTo("ACCEPTED");

    TimeUnit.MILLISECONDS.sleep(300);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("portal", "ALL");
    assertThat(status.details()).containsEntry("successCount", 4);
    assertThat(status.details()).containsEntry("failedCount", 1);

    @SuppressWarnings("unchecked")
    Map<String, Object> portals = (Map<String, Object>) status.details().get("portals");
    @SuppressWarnings("unchecked")
    Map<String, Object> jumpitResult = (Map<String, Object>) portals.get("JUMPIT");
    assertThat(jumpitResult).containsEntry("status", "FAILED");
    assertThat(jumpitResult).containsKey("errorMessage");
  }

  @Test
  @DisplayName("triggerCrawl ALL - 5개 포털 모두 실패 시 전체 상태가 FAILED 로 끝난다")
  void triggerCrawl_allPortalsAllFailed() throws Exception {
    given(jobPostingIngestService.crawlAndIngestJobKorea(any(), anyInt()))
        .willThrow(new RuntimeException("잡코리아 실패"));
    given(jobPostingIngestService.crawlAndIngestSaramin(anyInt()))
        .willThrow(new RuntimeException("사람인 실패"));
    given(jumpitIngestService.ingestAll()).willThrow(new RuntimeException("점핏 실패"));
    given(wantedIngestService.ingestAll()).willThrow(new RuntimeException("원티드 실패"));
    given(programmersIngestService.ingest(any())).willThrow(new RuntimeException("프로그래머스 실패"));

    JobExecutionResponse response =
        service.triggerCrawl(new CrawlRequest("ALL", null, 5, null, null, null));
    assertThat(response.status()).isEqualTo("ACCEPTED");

    TimeUnit.MILLISECONDS.sleep(300);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("FAILED");
    assertThat(status.errorMessage()).contains("모든 포털 수집에 실패했습니다");
  }

  @Test
  @DisplayName("triggerCrawl PROGRAMMERS - 프로그래머스 수집 성공 시 완료 상태가 된다")
  void triggerCrawl_programmersSuccess() throws Exception {
    given(programmersIngestService.ingest(any()))
        .willReturn(
            new com.carix.jobposting.programmers.ProgrammersCrawlSummary(
                2, 40, 30, 10, 0, 0, Map.of(), false));

    JobExecutionResponse response =
        service.triggerCrawl(new CrawlRequest("PROGRAMMERS", null, 2, null, null, null));
    assertThat(response.status()).isEqualTo("ACCEPTED");

    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("portal", "PROGRAMMERS");
    assertThat(status.details()).containsEntry("created", 30);
  }

  @Test
  @DisplayName("triggerCrawl JUMPIT - 처리한 공고가 하나도 없고 실패만 있으면 작업 실패다")
  void triggerCrawl_jumpitAllFailed() throws Exception {
    given(jumpitIngestService.ingestAll())
        .willReturn(new JumpitIngestSummary(2, 30, 0, 0, 0, 30, Map.of(), false));

    JobExecutionResponse response =
        service.triggerCrawl(new CrawlRequest("JUMPIT", null, null, null, null, null));
    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("FAILED");
    assertThat(status.errorMessage()).contains("errors=30");
  }

  @Test
  @DisplayName("triggerCrawl JUMPIT - 목록을 한 페이지도 못 받으면 작업 실패다")
  void triggerCrawl_jumpitListFailed() throws Exception {
    given(jumpitIngestService.ingestAll())
        .willReturn(new JumpitIngestSummary(0, 0, 0, 0, 0, 0, Map.of(), true));

    JobExecutionResponse response =
        service.triggerCrawl(new CrawlRequest("JUMPIT", null, null, null, null, null));
    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("FAILED");
    assertThat(status.errorMessage()).contains("점핏 목록을 받지 못했습니다");
  }

  @Test
  @DisplayName("triggerBackfill 원티드 - 일부 달만 저장돼도 성공이다, 저장 0건이고 오류만 있으면 실패다")
  void triggerBackfill_wantedFailureGuard() throws Exception {
    given(wantedBackfillService.run(any()))
        .willReturn(
            Map.of(
                "202509", new BackfillSampler.Result(10, 3, 0, 0, 0, 0, 2),
                "202510", new BackfillSampler.Result(10, 0, 0, 0, 0, 0, 5),
                "apiMisses", 0));
    JobExecutionResponse ok =
        service.triggerBackfill(
            new BackfillRequest("202509", "202510", 10, 100, 0L, null, null, "WANTED"));
    TimeUnit.MILLISECONDS.sleep(200);
    assertThat(service.getJobStatus(ok.jobId()).status()).isEqualTo("SUCCESS");

    given(wantedBackfillService.run(any()))
        .willReturn(
            Map.of(
                "202509",
                new BackfillSampler.Result(10, 0, 0, 0, 0, 0, 5),
                "droppedSkills",
                Map.of()));
    JobExecutionResponse bad =
        service.triggerBackfill(
            new BackfillRequest("202509", "202509", 10, 100, 0L, null, null, "WANTED"));
    TimeUnit.MILLISECONDS.sleep(200);
    JobStatusResponse status = service.getJobStatus(bad.jobId());
    assertThat(status.status()).isEqualTo("FAILED");
    assertThat(status.errorMessage()).contains("errors=5");
  }

  @Test
  @DisplayName("triggerBackfill - source=WANTED 는 원티드 백필로 보낸다")
  void triggerBackfill_routesWanted() throws Exception {
    given(wantedBackfillService.run(any())).willReturn(Map.of("202509", "wanted"));

    JobExecutionResponse response =
        service.triggerBackfill(
            new BackfillRequest("202509", "202509", 10, 100, 0L, null, null, "wanted"));

    TimeUnit.MILLISECONDS.sleep(200);
    assertThat(service.getJobStatus(response.jobId()).details()).containsEntry("202509", "wanted");
    org.mockito.Mockito.verifyNoInteractions(jobKoreaBackfillService);
  }

  @Test
  @DisplayName("triggerBackfill - 알 수 없는 source 는 400 이고 작업 잠금을 잡지 않는다")
  void triggerBackfill_rejectsUnknownSource() {
    assertThatThrownBy(
            () ->
                service.triggerBackfill(
                    new BackfillRequest("202509", "202509", null, null, null, null, null, "X")))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> {
              assertThat(e.getStatusCode().value()).isEqualTo(400);
              assertThat(e.getReason()).isEqualTo("source 는 JOBKOREA 또는 WANTED 입니다");
            });
    given(jobPostingIngestService.crawlAndIngest(any())).willReturn(1);
    assertThat(service.triggerCrawl(null).status()).isEqualTo("ACCEPTED");
  }

  @Test
  @DisplayName("triggerBackfill - 월 구간이 없으면 400 으로 거절하고 작업 잠금을 잡지 않는다")
  void triggerBackfill_requiresMonths() {
    assertThatThrownBy(() -> service.triggerBackfill(null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                service.triggerBackfill(
                    new BackfillRequest("202509", null, null, null, null, null, null, null)))
        .isInstanceOf(ResponseStatusException.class);

    given(jobPostingIngestService.crawlAndIngest(any())).willReturn(1);
    assertThat(service.triggerCrawl(null).status()).isEqualTo("ACCEPTED");
  }

  @Test
  @DisplayName("이미 작업 실행 중일 때 신규 작업 요청 시 409 Conflict 발생")
  void testConflictWhenJobRunning() {
    given(jobPostingIngestService.crawlAndIngest(any()))
        .willAnswer(
            inv -> {
              TimeUnit.MILLISECONDS.sleep(500);
              return 1;
            });

    service.triggerCrawl(new CrawlRequest("JOBKOREA", null, 1, null, null, null));

    assertThatThrownBy(() -> service.triggerDeduplication())
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("다른 파이프라인 작업이 이미 진행 중입니다.");
  }

  @Test
  @DisplayName("getJobStatus - 존재하지 않는 작업은 404 ResponseStatusException")
  void getJobStatus_notFound() {
    assertThatThrownBy(() -> service.getJobStatus("non-existent"))
        .isInstanceOf(ResponseStatusException.class);
  }
}
