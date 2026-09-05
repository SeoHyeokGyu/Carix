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
  @DisplayName("POST /internal/pipeline/seed-load - 시드 데이터 적재 비동기 요청 시 202 Accepted 반환")
  void triggerSeedLoadReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse("job-seed-1", "SEED_LOAD", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerSeedLoad(any())).thenReturn(response);

    mockMvc
        .perform(
            post("/internal/pipeline/seed-load")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"portal\":\"JOBKOREA\",\"seedDirPath\":\"/data\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-seed-1"))
        .andExpect(jsonPath("$.jobType").value("SEED_LOAD"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/wage-stat - KOSIS 임금 수집 비동기 요청 시 202 Accepted 반환")
  void triggerWageStatReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse(
            "job-789", "WAGE_STAT_INGEST", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerWageStatIngest(any())).thenReturn(response);

    mockMvc
        .perform(post("/internal/pipeline/wage-stat"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-789"))
        .andExpect(jsonPath("$.jobType").value("WAGE_STAT_INGEST"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/wage-stat with body - 기간 지정 비동기 요청 시 202 Accepted 반환")
  void triggerWageStatWithPeriodReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse(
            "job-790", "WAGE_STAT_INGEST", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerWageStatIngest(any())).thenReturn(response);

    mockMvc
        .perform(
            post("/internal/pipeline/wage-stat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startYear\":\"2020\",\"endYear\":\"2024\"}"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-790"))
        .andExpect(jsonPath("$.jobType").value("WAGE_STAT_INGEST"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/dedup - 공고 중복제거 비동기 요청 시 202 Accepted 반환")
  void triggerDedupReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse("job-999", "DEDUPLICATION", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerDeduplication()).thenReturn(response);

    mockMvc
        .perform(post("/internal/pipeline/dedup"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-999"))
        .andExpect(jsonPath("$.jobType").value("DEDUPLICATION"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/aggregate - 시장 사전집계 비동기 요청 시 202 Accepted 반환 (Query Param)")
  void triggerAggregateReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse(
            "job-888", "MARKET_AGGREGATION", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerMarketAggregation(
            any(com.carix.pipeline.dto.MarketAggregateRequest.class)))
        .thenReturn(response);

    mockMvc
        .perform(post("/internal/pipeline/aggregate?month=2026-08"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-888"))
        .andExpect(jsonPath("$.jobType").value("MARKET_AGGREGATION"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/aggregate - 시장 사전집계 연도구간 RequestBody 요청 시 202 Accepted 반환")
  void triggerAggregateWithBodyReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse(
            "job-889", "MARKET_AGGREGATION", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerMarketAggregation(
            any(com.carix.pipeline.dto.MarketAggregateRequest.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            post("/internal/pipeline/aggregate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "startYear": "2020",
                      "endYear": "2025"
                    }
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-889"))
        .andExpect(jsonPath("$.jobType").value("MARKET_AGGREGATION"));
  }

  @Test
  @DisplayName("POST /internal/pipeline/aggregate - 파라미터 없을 시 전체 사전집계 비동기 요청 202 Accepted 반환")
  void triggerAggregateDefaultAllReturnsAccepted() throws Exception {
    JobExecutionResponse response =
        new JobExecutionResponse(
            "job-890", "MARKET_AGGREGATION", "ACCEPTED", "Started", Instant.now());
    when(pipelineService.triggerMarketAggregation(
            any(com.carix.pipeline.dto.MarketAggregateRequest.class)))
        .thenReturn(response);

    mockMvc
        .perform(post("/internal/pipeline/aggregate"))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.jobId").value("job-890"))
        .andExpect(jsonPath("$.jobType").value("MARKET_AGGREGATION"));
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
