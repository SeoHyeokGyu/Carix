package com.carix.pipeline.service;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.seed.SeedLoader;
import com.carix.jobposting.service.JobPostingIngestService;
import com.carix.pipeline.dto.CrawlRequest;
import com.carix.pipeline.dto.JobExecutionResponse;
import com.carix.pipeline.dto.JobStatusResponse;
import com.carix.pipeline.dto.MarketAggregateRequest;
import com.carix.pipeline.dto.PipelineStatsResponse;
import com.carix.pipeline.dto.SeedLoadRequest;
import com.carix.pipeline.dto.WageStatRequest;
import com.carix.wagestat.WageStatIngestService;
import com.carix.wagestat.WageStatObservationRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 수집기 파이프라인 총괄 오케스트레이션 및 비동기 작업 생명주기 관리 서비스.
 *
 * <p>크롤링, KOSIS 임금 수집, 중복제거, 시장 사전집계, 원문 백필 등 단일 스레드 기반 순차 실행을 보장하며 인메모리 작업 상태 큐(최근 100건)를 통해 진행
 * 상황을 추적합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorPipelineService {

  private final JobPostingRepository jobPostingRepository;
  private final RawIngestRepository rawIngestRepository;
  private final SkillRepository skillRepository;
  private final WageStatObservationRepository wageStatObservationRepository;
  private final com.carix.market.MarketAggregateRepository marketAggregateRepository;
  private final JobPostingIngestService jobPostingIngestService;
  private final WageStatIngestService wageStatIngestService;
  private final com.carix.jobposting.dedup.JobPostingDeduplicator jobPostingDeduplicator;
  private final com.carix.market.MarketAggregateService marketAggregateService;

  private final AtomicBoolean isJobRunning = new AtomicBoolean(false);
  private final Map<String, JobState> jobHistory =
      Collections.synchronizedMap(
          new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, JobState> eldest) {
              return size() > 100;
            }
          });

  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  /**
   * 외부 채용 포털(잡코리아, 사람인) 크롤링 및 DB 적재 작업을 비동기로 실행합니다.
   *
   * @param request 크롤링 대상 포털 및 페이지 옵션
   * @return 비동기 작업 수락 응답(JobExecutionResponse)
   */
  public JobExecutionResponse triggerCrawl(CrawlRequest request) {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "CRAWL", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            log.info("[작업:{}] 채용공고 크롤링 및 DB 적재 작업을 시작합니다...", jobId);
            int ingested = jobPostingIngestService.crawlAndIngest(request);
            state.complete(Map.of("ingestedJobPostings", ingested));
            log.info("[작업:{}] 채용공고 크롤링 완료. 총 {}건의 공고가 DB에 적재되었습니다.", jobId, ingested);
          } catch (Exception e) {
            log.error("[작업:{}] 채용공고 크롤링 실패: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "CRAWL", "ACCEPTED", "크롤링 작업이 수락되어 백그라운드에서 시작되었습니다.", now);
  }

  /**
   * 로컬/서버 파일시스템의 스냅샷 시드 데이터를 DB로 적재합니다.
   *
   * @param request 시드 디렉토리 경로
   * @return 비동기 작업 수락 응답
   */
  public JobExecutionResponse triggerSeedLoad(SeedLoadRequest request) {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "SEED_LOAD", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            String outDir =
                request != null && request.dataDir() != null
                    ? request.dataDir()
                    : resolveDefaultOutputDir();
            log.info("[작업:{}] 파일 시드 데이터 적재를 시작합니다. 경로: {}", jobId, outDir);
            SeedLoader loader =
                new SeedLoader(jobPostingRepository, skillRepository, rawIngestRepository);
            int loaded = loader.load(Path.of(outDir));
            state.complete(Map.of("loadedJobPostings", loaded));
            log.info("[작업:{}] 시드 데이터 적재 완료. 총 {}건 적재됨.", jobId, loaded);
          } catch (Exception e) {
            log.error("[작업:{}] 시드 데이터 적재 실패: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "SEED_LOAD", "ACCEPTED", "시드 적재 작업이 수락되어 백그라운드에서 시작되었습니다.", now);
  }

  /**
   * KOSIS 국가 공식 임금 통계를 실시간 수신하여 24구간 보간 후 DB에 적재합니다. (최신 1개 연도)
   *
   * @return 비동기 작업 수락 응답
   */
  public JobExecutionResponse triggerWageStatIngest() {
    return triggerWageStatIngest(null);
  }

  /**
   * KOSIS 국가 공식 임금 통계를 지정한 연도 구간으로 수신하여 24구간 보간 후 DB에 적재합니다.
   *
   * @param request 연도 범위 요청 DTO (null 시 최신 1개 연도)
   * @return 비동기 작업 수락 응답
   */
  public JobExecutionResponse triggerWageStatIngest(WageStatRequest request) {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "WAGE_STAT_INGEST", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            String startYear = request != null ? request.startYear() : null;
            String endYear = request != null ? request.endYear() : null;
            log.info(
                "[작업:{}] KOSIS 임금 통계 수집 및 보간 적재 작업을 시작합니다... (기간: {} ~ {})",
                jobId,
                startYear != null ? startYear : "최신",
                endYear != null ? endYear : (startYear != null ? startYear : "최신"));

            int count = wageStatIngestService.ingestWageStats(startYear, endYear);
            state.complete(
                Map.of(
                    "ingestedWageStats", count,
                    "startYear", startYear != null ? startYear : "LATEST",
                    "endYear",
                        endYear != null ? endYear : (startYear != null ? startYear : "LATEST")));
            log.info("[작업:{}] KOSIS 임금 통계 수집 완료. 총 {}개 관측치가 저장되었습니다.", jobId, count);
          } catch (Exception e) {
            log.error("[작업:{}] KOSIS 임금 통계 수집 실패: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "WAGE_STAT_INGEST", "ACCEPTED", "KOSIS 임금 통계 수집 작업이 수락되어 백그라운드에서 시작되었습니다.", now);
  }

  /**
   * 원천 간(잡코리아-사람인) 채용공고 중복제거(Deduplication)를 실행합니다.
   *
   * @return 비동기 작업 수락 응답
   */
  public JobExecutionResponse triggerDeduplication() {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "DEDUPLICATION", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            log.info("[작업:{}] 원천 간 채용공고 중복제거 배치를 시작합니다...", jobId);
            int groups = jobPostingDeduplicator.deduplicateAll();
            state.complete(Map.of("dedupMatchedGroups", groups));
            log.info("[작업:{}] 공고 중복제거 완료. 총 {}개의 다중 공고 그룹이 형성되었습니다.", jobId, groups);
          } catch (Exception e) {
            log.error("[작업:{}] 공고 중복제거 실패: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "DEDUPLICATION", "ACCEPTED", "공고 중복제거 작업이 수락되어 백그라운드에서 시작되었습니다.", now);
  }

  /**
   * 특정 월 기준 채용 시장 다차원 사전 집계(Pre-aggregation)를 실행합니다.
   *
   * @param baseYearMonth 기준 년월 (형식: "YYYY-MM" 또는 "YYYYMM")
   * @return 비동기 작업 수락 응답
   */
  public JobExecutionResponse triggerMarketAggregation(String baseYearMonth) {
    return triggerMarketAggregation(
        new MarketAggregateRequest(baseYearMonth, null, null, null, null));
  }

  /**
   * 채용 시장 다차원 사전 집계(Pre-aggregation)를 요청 옵션(단일월/연도/연도구간/전체)에 따라 실행합니다.
   *
   * @param request 집계 요청 옵션
   * @return 비동기 작업 수락 응답
   */
  public JobExecutionResponse triggerMarketAggregation(MarketAggregateRequest request) {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "MARKET_AGGREGATION", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            log.info("[작업:{}] 채용 시장 다차원 사전 집계 작업을 시작합니다 (옵션: {})...", jobId, request);
            int rows = marketAggregateService.aggregateMarketData(request);
            state.complete(Map.of("aggregatedRows", rows));
            log.info("[작업:{}] 채용 시장 사전 집계 완료. 총 {}개의 집계 레코드가 생성되었습니다.", jobId, rows);
          } catch (Exception e) {
            log.error("[작업:{}] 채용 시장 사전 집계 실패: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "MARKET_AGGREGATION", "ACCEPTED", "시장 사전 집계 작업이 수락되어 백그라운드에서 시작되었습니다.", now);
  }

  /**
   * 비동기 작업 상태를 단건 조회합니다.
   *
   * @param jobId 작업 ID
   * @return 작업 상태 응답 DTO
   */
  public JobStatusResponse getJobStatus(String jobId) {
    JobState state = jobHistory.get(jobId);
    if (state == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다: " + jobId);
    }
    return state.toResponse();
  }

  /**
   * 최근 작업 실행 이력 목록을 최신순으로 조회합니다.
   *
   * @return 최근 최대 100건의 작업 이력 목록
   */
  public List<JobStatusResponse> getRecentJobs() {
    List<JobState> states = new ArrayList<>(jobHistory.values());
    Collections.reverse(states);
    return states.stream().map(JobState::toResponse).toList();
  }

  /**
   * 수집기 DB 전체 데이터 건수 및 통계를 조회합니다.
   *
   * @return raw_ingest, job_posting, skill, wage_stat 건수 및 포털별 분포
   */
  public PipelineStatsResponse getStats() {
    long rawCount = rawIngestRepository.count();
    long postingCount = jobPostingRepository.count();
    long skillCount = skillRepository.count();
    long wageCount = wageStatObservationRepository.count();

    Map<String, Long> bySource = new HashMap<>();
    try {
      bySource.put("JOBKOREA", jobPostingRepository.countBySource("JOBKOREA"));
      bySource.put("SARAMIN", jobPostingRepository.countBySource("SARAMIN"));
    } catch (Exception ignored) {
    }

    return new PipelineStatsResponse(rawCount, postingCount, skillCount, wageCount, bySource);
  }

  private void ensureNoJobRunning() {
    if (!isJobRunning.compareAndSet(false, true)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 파이프라인 작업이 이미 진행 중입니다.");
    }
  }

  private String resolveDefaultOutputDir() {
    Path candidate = Path.of("backend/collector/data/output");
    if (Files.isDirectory(candidate)) {
      return candidate.toAbsolutePath().toString();
    }
    candidate = Path.of("data/output");
    if (Files.isDirectory(candidate)) {
      return candidate.toAbsolutePath().toString();
    }
    return Path.of(System.getProperty("user.dir"), "data/output").toString();
  }

  private static class JobState {
    private final String jobId;
    private final String jobType;
    private String status;
    private final Instant startedAt;
    private Instant completedAt;
    private Map<String, Object> details;
    private String errorMessage;

    public JobState(String jobId, String jobType, String status, Instant startedAt) {
      this.jobId = jobId;
      this.jobType = jobType;
      this.status = status;
      this.startedAt = startedAt;
    }

    public synchronized void complete(Map<String, Object> details) {
      this.status = "SUCCESS";
      this.completedAt = Instant.now();
      this.details = details;
    }

    public synchronized void fail(String errorMessage) {
      this.status = "FAILED";
      this.completedAt = Instant.now();
      this.errorMessage = errorMessage;
    }

    public synchronized JobStatusResponse toResponse() {
      return new JobStatusResponse(
          jobId, jobType, status, startedAt, completedAt, details, errorMessage);
    }
  }
}
