package com.carix.pipeline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.pipeline.dto.CrawlRequest;
import com.carix.pipeline.dto.JobExecutionResponse;
import com.carix.pipeline.dto.JobStatusResponse;
import com.carix.pipeline.dto.PipelineStatsResponse;
import com.carix.pipeline.dto.ReprocessRequest;
import com.carix.pipeline.service.CollectorPipelineService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
                .content("{\"portal\":\"JOBKOREA\",\"maxPages\":5}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-123"))
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/reprocess - 원본 재가공 비동기 요청 시 202 Accepted 반환")
  void triggerReprocessReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse("job-456", "REPROCESS", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerReprocess(any(ReprocessRequest.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/internal/pipeline/reprocess")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dryRun\":true}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-456"))
        .andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/wage-stat - KOSIS 임금 수집 비동기 요청 시 202 Accepted 반환")
  void triggerWageStatReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse(
            "job-789", "WAGE_STAT_INGEST", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerWageStatIngest()).thenReturn(response);

    mockMvc
        .perform(post("/internal/pipeline/wage-stat"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-789"))
        .andExpect(jsonPath("$.jobType").value("WAGE_STAT_INGEST"));
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
    PipelineStatsResponse response =
        new PipelineStatsResponse(400, 395, 120, 24, Map.of("JOBKOREA", 200L, "SARAMIN", 195L));
    when(pipelineService.getStats()).thenReturn(response);

    mockMvc
        .perform(get("/internal/pipeline/stats"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRawIngest").value(400))
        .andExpect(jsonPath("$.totalJobPostings").value(395))
        .andExpect(jsonPath("$.totalSkills").value(120))
        .andExpect(jsonPath("$.totalWageStats").value(24));
  }
}
