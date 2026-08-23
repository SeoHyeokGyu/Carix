package com.carix.collector.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.collector.dto.CrawlRequest;
import com.carix.collector.dto.JobExecutionResponse;
import com.carix.collector.dto.JobStatusResponse;
import com.carix.collector.dto.PipelineStatsResponse;
import com.carix.collector.service.CollectorPipelineService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CollectorPipelineControllerTest {

  private MockMvc mockMvc;
  private CollectorPipelineService pipelineService;

  @BeforeEach
  void setUp() {
    pipelineService = mock(CollectorPipelineService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new CollectorPipelineController(pipelineService)).build();
  }

  @Test
  @DisplayName("POST /internal/pipeline/crawl - 크롤링 비동기 요청 시 202 Accepted 반환")
  void triggerCrawlReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse("job-123", "CRAWL", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerCrawl(any(CrawlRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/internal/pipeline/crawl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"JOBKOREA\",\"pageLimit\":5}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-123"))
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/backfill - 백필 비동기 요청 시 202 Accepted 반환")
  void triggerBackfillReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse("job-456", "BACKFILL", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerBackfill(any())).thenReturn(response);

    mockMvc
        .perform(
            post("/internal/pipeline/backfill")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dryRun\":true}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-456"))
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  @DisplayName("GET /internal/pipeline/jobs/{jobId} - 작업 상태 조회 시 200 OK 반환")
  void getJobStatusReturnsOk() throws Exception {
    JobStatusResponse response =
        new JobStatusResponse(
            "job-123", "CRAWL", "COMPLETED", Instant.now(), Instant.now(), null, null);
    when(pipelineService.getJobStatus("job-123")).thenReturn(response);

    mockMvc
        .perform(get("/internal/pipeline/jobs/job-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.jobId").value("job-123"))
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  @DisplayName("GET /internal/pipeline/jobs - 최근 작업 목록 조회 시 200 OK 반환")
  void getRecentJobsReturnsOk() throws Exception {
    JobStatusResponse response =
        new JobStatusResponse(
            "job-123", "CRAWL", "COMPLETED", Instant.now(), Instant.now(), null, null);
    when(pipelineService.getRecentJobs()).thenReturn(List.of(response));

    mockMvc
        .perform(get("/internal/pipeline/jobs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].jobId").value("job-123"));
  }

  @Test
  @DisplayName("GET /internal/pipeline/stats - DB 수집 통계 조회 시 200 OK 반환")
  void getStatsReturnsOk() throws Exception {
    PipelineStatsResponse response = new PipelineStatsResponse(400, 395, 120, Instant.now());
    when(pipelineService.getStats()).thenReturn(response);

    mockMvc
        .perform(get("/internal/pipeline/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rawIngestCount").value(400))
        .andExpect(jsonPath("$.jobPostingCount").value(395))
        .andExpect(jsonPath("$.skillCount").value(120));
  }
}
