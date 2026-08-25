package com.carix.pipeline.service;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.backfill.BackfillReport;
import com.carix.jobposting.backfill.JobPostingBackfill;
import com.carix.jobposting.seed.SeedLoader;
import com.carix.jobposting.service.JobPostingIngestService;
import com.carix.pipeline.dto.CrawlRequest;
import com.carix.pipeline.dto.JobExecutionResponse;
import com.carix.pipeline.dto.JobStatusResponse;
import com.carix.pipeline.dto.PipelineStatsResponse;
import com.carix.pipeline.dto.ReprocessRequest;
import com.carix.pipeline.dto.SeedLoadRequest;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorPipelineService {

  private final JobPostingRepository jobPostingRepository;
  private final RawIngestRepository rawIngestRepository;
  private final SkillRepository skillRepository;
  private final WageStatObservationRepository wageStatObservationRepository;
  private final JobPostingBackfill jobPostingBackfill;
  private final JobPostingIngestService jobPostingIngestService;
  private final WageStatIngestService wageStatIngestService;

  private final AtomicBoolean isJobRunning = new AtomicBoolean(false);
  private final Map<String, JobState> jobHistory =
      Collections.synchronizedMap(
          new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, JobState> eldest) {
              return size() > 100;
            }
          });

  private final ExecutorService executor = Executors.newSingleThreadExecutor();

  public JobExecutionResponse triggerCrawl(CrawlRequest request) {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "CRAWL", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            log.info("[Job:{}] Starting crawl and ingest into DB...", jobId);
            int ingested = jobPostingIngestService.crawlAndIngest(request);
            state.complete(Map.of("ingestedJobPostings", ingested));
            log.info("[Job:{}] Crawl completed. Total ingested to DB: {}", jobId, ingested);
          } catch (Exception e) {
            log.error("[Job:{}] Crawl failed: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "CRAWL", "ACCEPTED", "Crawl job accepted and started in background", now);
  }

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
            log.info("[Job:{}] Loading seed data from: {}", jobId, outDir);
            SeedLoader loader =
                new SeedLoader(jobPostingRepository, skillRepository, rawIngestRepository);
            int loaded = loader.load(Path.of(outDir));
            state.complete(Map.of("loadedJobPostings", loaded));
            log.info("[Job:{}] Seed data loading completed. Total loaded: {}", jobId, loaded);
          } catch (Exception e) {
            log.error("[Job:{}] Seed data loading failed: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "SEED_LOAD", "ACCEPTED", "Seed load job accepted and started in background", now);
  }

  public JobExecutionResponse triggerWageStatIngest() {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "WAGE_STAT_INGEST", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            log.info("[Job:{}] Triggering KOSIS wage stat ingestion...", jobId);
            int count = wageStatIngestService.ingestWageStats();
            state.complete(Map.of("ingestedWageStats", count));
            log.info("[Job:{}] KOSIS wage stat ingestion completed: {} rows", jobId, count);
          } catch (Exception e) {
            log.error("[Job:{}] KOSIS wage stat ingestion failed: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId,
        "WAGE_STAT_INGEST",
        "ACCEPTED",
        "Wage stat ingest job accepted and started in background",
        now);
  }

  public JobExecutionResponse triggerReprocess(ReprocessRequest request) {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "REPROCESS", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
            log.info("[Job:{}] Starting reprocess (dryRun={})...", jobId, dryRun);
            BackfillReport report = jobPostingBackfill.run(dryRun);
            Map<String, Object> details = new HashMap<>();
            details.put("dryRun", dryRun);
            details.put("postingsScanned", report.postingsScanned());
            details.put("salaryFilled", report.salaryFilled());
            details.put("jobFamilyFilled", report.jobFamilyFilled());
            details.put("companySizeFilled", report.companySizeFilled());
            details.put("employmentTypeFilled", report.employmentTypeFilled());
            details.put("experienceCorrected", report.experienceCorrected());
            details.put("skillLinksRewritten", report.skillLinksRewritten());
            details.put("skillsRecategorized", report.skillsRecategorized());
            details.put("orphanSkillsRemoved", report.orphanSkillsRemoved());
            details.put("rawIngestMissing", report.rawIngestMissing());
            state.complete(details);
            log.info("[Job:{}] Reprocess completed successfully: {}", jobId, report);
          } catch (Exception e) {
            log.error("[Job:{}] Reprocess failed: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "REPROCESS", "ACCEPTED", "Reprocess job accepted and started in background", now);
  }

  public JobStatusResponse getJobStatus(String jobId) {
    JobState state = jobHistory.get(jobId);
    if (state == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
    }
    return state.toResponse();
  }

  public List<JobStatusResponse> getRecentJobs() {
    List<JobState> states = new ArrayList<>(jobHistory.values());
    Collections.reverse(states);
    return states.stream().map(JobState::toResponse).toList();
  }

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
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Another pipeline job is already in progress.");
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
