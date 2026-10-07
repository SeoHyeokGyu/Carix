package com.carix.pipeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.cpi.CpiIngestService;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.backfill.JobKoreaBackfillService;
import com.carix.jobposting.dedup.JobPostingDeduplicator;
import com.carix.jobposting.service.JobPostingIngestService;
import com.carix.market.MarketAggregateRepository;
import com.carix.market.MarketAggregateService;
import com.carix.pipeline.dto.BackfillRequest;
import com.carix.pipeline.dto.CrawlRequest;
import com.carix.pipeline.dto.JobExecutionResponse;
import com.carix.pipeline.dto.JobStatusResponse;
import com.carix.pipeline.dto.MarketAggregateRequest;
import com.carix.pipeline.dto.PipelineStatsResponse;
import com.carix.pipeline.dto.SeedLoadRequest;
import com.carix.pipeline.dto.WageStatRequest;
import com.carix.wagestat.WageStatIngestService;
import com.carix.wagestat.WageStatObservationRepository;
import java.util.List;
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
  private WageStatIngestService wageStatIngestService;
  private CpiIngestService cpiIngestService;
  private JobPostingDeduplicator jobPostingDeduplicator;
  private MarketAggregateService marketAggregateService;
  private JobKoreaBackfillService jobKoreaBackfillService;

  private CollectorPipelineService service;

  @BeforeEach
  void setUp() {
    jobPostingRepository = mock(JobPostingRepository.class);
    rawIngestRepository = mock(RawIngestRepository.class);
    skillRepository = mock(SkillRepository.class);
    wageStatObservationRepository = mock(WageStatObservationRepository.class);
    marketAggregateRepository = mock(MarketAggregateRepository.class);
    jobPostingIngestService = mock(JobPostingIngestService.class);
    wageStatIngestService = mock(WageStatIngestService.class);
    cpiIngestService = mock(CpiIngestService.class);
    jobPostingDeduplicator = mock(JobPostingDeduplicator.class);
    marketAggregateService = mock(MarketAggregateService.class);
    jobKoreaBackfillService = mock(JobKoreaBackfillService.class);

    service =
        new CollectorPipelineService(
            jobPostingRepository,
            rawIngestRepository,
            skillRepository,
            wageStatObservationRepository,
            marketAggregateRepository,
            jobPostingIngestService,
            wageStatIngestService,
            cpiIngestService,
            jobPostingDeduplicator,
            marketAggregateService,
            jobKoreaBackfillService,
            mock(com.carix.jobposting.backfill.SalaryReclassifyService.class),
            mock(com.carix.jobposting.jumpit.JumpitIngestService.class));
  }

  @Test
  @DisplayName("triggerCrawl - 비동기 크롤링 작업 수락 및 상태 완료 확인")
  void triggerCrawl_success() throws Exception {
    given(jobPostingIngestService.crawlAndIngest(any())).willReturn(10);

    JobExecutionResponse response =
        service.triggerCrawl(new CrawlRequest("JOBKOREA", null, 2, null, null, null));

    assertThat(response.status()).isEqualTo("ACCEPTED");
    assertThat(response.jobType()).isEqualTo("CRAWL");

    // 비동기 완료 대기
    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("ingestedJobPostings", 10);
  }

  @Test
  @DisplayName("triggerBackfill - 비동기 백필 작업 수락 및 월별 요약 기록")
  void triggerBackfill_success() throws Exception {
    given(jobKoreaBackfillService.run(any())).willReturn(Map.of("202509", "ok"));

    JobExecutionResponse response =
        service.triggerBackfill(new BackfillRequest("202509", "202509", 10, 100, 0L, null, null));

    assertThat(response.jobType()).isEqualTo("BACKFILL");
    TimeUnit.MILLISECONDS.sleep(200);
    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("202509", "ok");
  }

  @Test
  @DisplayName("triggerBackfill - 월 구간이 없으면 400 으로 거절하고 작업 잠금을 잡지 않는다")
  void triggerBackfill_requiresMonths() {
    assertThatThrownBy(() -> service.triggerBackfill(null))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(
            () ->
                service.triggerBackfill(
                    new BackfillRequest("202509", null, null, null, null, null, null)))
        .isInstanceOf(ResponseStatusException.class);

    given(jobPostingIngestService.crawlAndIngest(any())).willReturn(1);
    assertThat(service.triggerCrawl(null).status()).isEqualTo("ACCEPTED");
  }

  @Test
  @DisplayName("triggerSkillRefine - 비동기 스킬 재정제 작업 수락 및 상태 완료 확인")
  void triggerSkillRefine_success() throws Exception {
    given(jobPostingIngestService.refineAllPostingSkills())
        .willReturn(Map.of("totalPostings", 100, "refinedPostings", 100, "postingsWithSkills", 80));

    JobExecutionResponse response = service.triggerSkillRefine();

    assertThat(response.status()).isEqualTo("ACCEPTED");
    assertThat(response.jobType()).isEqualTo("SKILL_REFINE");

    // 비동기 완료 대기
    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("totalPostings", 100);
    assertThat(status.details()).containsEntry("postingsWithSkills", 80);
  }

  @Test
  @DisplayName("triggerCrawl - 예외 발생 시 FAILED 상태 기록")
  void triggerCrawl_failure() throws Exception {
    given(jobPostingIngestService.crawlAndIngest(any())).willThrow(new RuntimeException("크롤링 에러"));

    JobExecutionResponse response =
        service.triggerCrawl(new CrawlRequest("ALL", null, 1, null, null, null));

    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("FAILED");
    assertThat(status.errorMessage()).contains("크롤링 에러");
  }

  @Test
  @DisplayName("triggerWageStatIngest - 기본 및 파라미터 전달 실행")
  void triggerWageStatIngest_success() throws Exception {
    given(wageStatIngestService.ingestWageStats(any(), any())).willReturn(24);

    JobExecutionResponse response1 = service.triggerWageStatIngest();
    assertThat(response1.jobType()).isEqualTo("WAGE_STAT_INGEST");

    TimeUnit.MILLISECONDS.sleep(200);

    JobExecutionResponse response2 =
        service.triggerWageStatIngest(new WageStatRequest("2020", "2024"));
    assertThat(response2.jobType()).isEqualTo("WAGE_STAT_INGEST");

    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response2.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
  }

  @Test
  @DisplayName("triggerCpiIngest - 기본 및 파라미터 전달 실행")
  void triggerCpiIngest_success() throws Exception {
    given(cpiIngestService.ingestCpi(any(), any())).willReturn(12);

    JobExecutionResponse response1 = service.triggerCpiIngest();
    assertThat(response1.jobType()).isEqualTo("CPI_INGEST");

    TimeUnit.MILLISECONDS.sleep(200);

    JobExecutionResponse response2 =
        service.triggerCpiIngest(new com.carix.pipeline.dto.CpiRequest("202401", "202412"));
    assertThat(response2.jobType()).isEqualTo("CPI_INGEST");

    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response2.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("ingestedCpiObservations", 12);
  }

  @Test
  @DisplayName("triggerDeduplication - 중복제거 실행")
  void triggerDeduplication_success() throws Exception {
    given(jobPostingDeduplicator.deduplicateAll()).willReturn(5);

    JobExecutionResponse response = service.triggerDeduplication();
    assertThat(response.jobType()).isEqualTo("DEDUPLICATION");

    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("dedupMatchedGroups", 5);
  }

  @Test
  @DisplayName("triggerMarketAggregation - 문자열 및 RequestBody 요청 실행")
  void triggerMarketAggregation_success() throws Exception {
    given(marketAggregateService.aggregateMarketData(any())).willReturn(100);

    JobExecutionResponse res1 = service.triggerMarketAggregation("202607");
    TimeUnit.MILLISECONDS.sleep(200);

    JobExecutionResponse res2 =
        service.triggerMarketAggregation(
            new MarketAggregateRequest(null, "2020", "2025", null, null));
    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(res2.jobId());
    assertThat(status.status()).isEqualTo("SUCCESS");
    assertThat(status.details()).containsEntry("aggregatedRows", 100);
  }

  @Test
  @DisplayName("triggerSeedLoad - 시드 적재 실행 (경로 미지정 시 기본 경로 해석)")
  void triggerSeedLoad_success() throws Exception {
    JobExecutionResponse response = service.triggerSeedLoad(new SeedLoadRequest("dummy/path"));
    assertThat(response.jobType()).isEqualTo("SEED_LOAD");

    TimeUnit.MILLISECONDS.sleep(200);

    JobStatusResponse status = service.getJobStatus(response.jobId());
    assertThat(status).isNotNull();

    // null 요청으로 resolveDefaultOutputDir 커버
    JobExecutionResponse responseNull = service.triggerSeedLoad(null);
    assertThat(responseNull).isNotNull();
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

  @Test
  @DisplayName("getRecentJobs - 최근 작업 목록 조회")
  void getRecentJobs() throws Exception {
    given(wageStatIngestService.ingestWageStats(any(), any())).willReturn(1);
    service.triggerWageStatIngest();
    TimeUnit.MILLISECONDS.sleep(200);

    List<JobStatusResponse> recent = service.getRecentJobs();
    assertThat(recent).isNotEmpty();
  }

  @Test
  @DisplayName("getStats - DB 수집 통계 정상 계산")
  void getStats() {
    given(rawIngestRepository.count()).willReturn(100L);
    given(jobPostingRepository.count()).willReturn(200L);
    given(skillRepository.count()).willReturn(50L);
    given(wageStatObservationRepository.count()).willReturn(24L);
    given(jobPostingRepository.countBySource("JOBKOREA")).willReturn(120L);
    given(jobPostingRepository.countBySource("SARAMIN")).willReturn(80L);

    PipelineStatsResponse stats = service.getStats();

    assertThat(stats.totalRawIngest()).isEqualTo(100L);
    assertThat(stats.totalJobPostings()).isEqualTo(200L);
    assertThat(stats.totalSkills()).isEqualTo(50L);
    assertThat(stats.totalWageStats()).isEqualTo(24L);
    assertThat(stats.postingsBySource()).containsEntry("JOBKOREA", 120L);
  }
}
