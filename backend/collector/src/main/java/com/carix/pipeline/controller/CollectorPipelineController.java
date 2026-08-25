package com.carix.pipeline.controller;

import com.carix.pipeline.dto.CrawlRequest;
import com.carix.pipeline.dto.JobExecutionResponse;
import com.carix.pipeline.dto.JobStatusResponse;
import com.carix.pipeline.dto.PipelineStatsResponse;
import com.carix.pipeline.dto.ReprocessRequest;
import com.carix.pipeline.dto.SeedLoadRequest;
import com.carix.pipeline.service.CollectorPipelineService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 수집기 파이프라인 관리 및 비동기 작업 제어 REST API 컨트롤러. */
@RestController
@RequestMapping("/internal/pipeline")
@RequiredArgsConstructor
public class CollectorPipelineController {

  private final CollectorPipelineService pipelineService;

  @PostMapping("/crawl")
  public ResponseEntity<JobExecutionResponse> triggerCrawl(
      @RequestBody(required = false) CrawlRequest request) {
    JobExecutionResponse response = pipelineService.triggerCrawl(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @PostMapping("/seed-load")
  public ResponseEntity<JobExecutionResponse> triggerSeedLoad(
      @RequestBody(required = false) SeedLoadRequest request) {
    JobExecutionResponse response = pipelineService.triggerSeedLoad(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @PostMapping("/reprocess")
  public ResponseEntity<JobExecutionResponse> triggerReprocess(
      @RequestBody(required = false) ReprocessRequest request) {
    ReprocessRequest req = request != null ? request : new ReprocessRequest(true);
    JobExecutionResponse response = pipelineService.triggerReprocess(req);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @PostMapping("/wage-stat")
  public ResponseEntity<JobExecutionResponse> triggerWageStatIngest() {
    JobExecutionResponse response = pipelineService.triggerWageStatIngest();
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @PostMapping("/dedup")
  public ResponseEntity<JobExecutionResponse> triggerDeduplication() {
    JobExecutionResponse response = pipelineService.triggerDeduplication();
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @GetMapping("/jobs/{jobId}")
  public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
    return ResponseEntity.ok(pipelineService.getJobStatus(jobId));
  }

  @GetMapping("/jobs")
  public ResponseEntity<List<JobStatusResponse>> getRecentJobs() {
    return ResponseEntity.ok(pipelineService.getRecentJobs());
  }

  @GetMapping("/stats")
  public ResponseEntity<PipelineStatsResponse> getStats() {
    return ResponseEntity.ok(pipelineService.getStats());
  }
}
