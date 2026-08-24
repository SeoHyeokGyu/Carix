package com.carix.collector.service;

import com.carix.collector.dto.CrawlRequest;
import com.carix.collector.dto.JobExecutionResponse;
import com.carix.collector.dto.JobStatusResponse;
import com.carix.collector.dto.PipelineStatsResponse;
import com.carix.collector.dto.SeedLoadRequest;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.seed.SeedLoader;
import com.carix.jobposting.service.JobPostingIngestService;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CollectorPipelineService {

  private static final Logger log = LoggerFactory.getLogger(CollectorPipelineService.class);

  private final JobPostingRepository jobPostingRepository;
  private final RawIngestRepository rawIngestRepository;
  private final SkillRepository skillRepository;
  private final JobPostingIngestService jobPostingIngestService;

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

  public CollectorPipelineService(
      JobPostingRepository jobPostingRepository,
      RawIngestRepository rawIngestRepository,
      SkillRepository skillRepository,
      JobPostingIngestService jobPostingIngestService) {
    this.jobPostingRepository = jobPostingRepository;
    this.rawIngestRepository = rawIngestRepository;
    this.skillRepository = skillRepository;
    this.jobPostingIngestService = jobPostingIngestService;
  }

  public JobExecutionResponse triggerCrawl(CrawlRequest request) {
    ensureNoJobRunning();
    String jobId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    JobState state = new JobState(jobId, "CRAWL", "RUNNING", now);
    jobHistory.put(jobId, state);

    CompletableFuture.runAsync(
        () -> {
          try {
            Map<String, Object> summary = new LinkedHashMap<>();

            if ("JOBKOREA".equalsIgnoreCase(request.source())
                || "ALL".equalsIgnoreCase(request.source())) {
              log.info("[Job:{}] Starting JobKorea crawling & direct DB ingestion...", jobId);
              int jkCount =
                  jobPostingIngestService.crawlAndIngestJobKorea(
                      request.keyword(), request.pageLimit());
              summary.put("jobkoreaIngestedCount", jkCount);
            }

            if ("SARAMIN".equalsIgnoreCase(request.source())
                || "ALL".equalsIgnoreCase(request.source())) {
              log.info("[Job:{}] Starting Saramin crawling & direct DB ingestion...", jobId);
              int smCount = jobPostingIngestService.crawlAndIngestSaramin(request.pageLimit());
              summary.put("saraminIngestedCount", smCount);
            }

            state.complete(summary);
            log.info("[Job:{}] Direct DB Ingestion completed: {}", jobId, summary);
          } catch (Exception e) {
            log.error("[Job:{}] Crawling/Ingestion failed: {}", jobId, e.getMessage(), e);
            state.fail(e.getMessage());
          } finally {
            isJobRunning.set(false);
          }
        },
        executor);

    return new JobExecutionResponse(
        jobId, "CRAWL", "ACCEPTED", "Direct DB Crawl & Ingest job started in background", now);
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

  public JobStatusResponse getJobStatus(String jobId) {
    JobState state = jobHistory.get(jobId);
    if (state == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId);
    }
    return state.toResponse();
  }

  public List<JobStatusResponse> getRecentJobs() {
    List<JobStatusResponse> list = new ArrayList<>();
    synchronized (jobHistory) {
      for (JobState state : jobHistory.values()) {
        list.add(state.toResponse());
      }
    }
    Collections.reverse(list);
    return list;
  }

  public PipelineStatsResponse getStats() {
    return new PipelineStatsResponse(
        rawIngestRepository.count(),
        jobPostingRepository.count(),
        skillRepository.count(),
        Instant.now());
  }

  private void ensureNoJobRunning() {
    if (!isJobRunning.compareAndSet(false, true)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Another pipeline job is already in progress.");
    }
  }

  private String resolveDefaultOutputDir() {
    File currentDir = new File(System.getProperty("user.dir"));
    File tempDir = currentDir;
    while (tempDir != null && !tempDir.getName().equals("Carix")) {
      tempDir = tempDir.getParentFile();
    }
    String rootPath = (tempDir != null) ? tempDir.getAbsolutePath() : currentDir.getAbsolutePath();
    return rootPath + "/verification/out";
  }

  private static class JobState {
    private final String jobId;
    private final String jobType;
    private String status;
    private final Instant startedAt;
    private Instant finishedAt;
    private Object result;
    private String errorMessage;

    public JobState(String jobId, String jobType, String status, Instant startedAt) {
      this.jobId = jobId;
      this.jobType = jobType;
      this.status = status;
      this.startedAt = startedAt;
    }

    public synchronized void complete(Object result) {
      this.status = "COMPLETED";
      this.finishedAt = Instant.now();
      this.result = result;
    }

    public synchronized void fail(String errorMessage) {
      this.status = "FAILED";
      this.finishedAt = Instant.now();
      this.errorMessage = errorMessage;
    }

    public synchronized JobStatusResponse toResponse() {
      return new JobStatusResponse(
          jobId, jobType, status, startedAt, finishedAt, result, errorMessage);
    }
  }
}
