package com.carix.pipeline.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.pipeline.dto.CrawlRequest;
import com.carix.pipeline.dto.JobExecutionResponse;
import com.carix.pipeline.service.CollectorPipelineService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CollectorPipelineControllerTest {

  private MockMvc mockMvc;
  private CollectorPipelineService pipelineService;
  private com.carix.pipeline.service.MviCollectionPriorityService priorityService;

  @BeforeEach
  void setUp() {
    pipelineService = mock(CollectorPipelineService.class);
    priorityService = mock(com.carix.pipeline.service.MviCollectionPriorityService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new CollectorPipelineController(
                    pipelineService,
                    priorityService,
                    mock(com.carix.pipeline.health.DataHealthService.class)))
            .build();
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
  @DisplayName("급여 재판정·잡코리아 재분류에 음수 delayMs 를 주면 400")
  void negativeDelayIsBadRequest() throws Exception {
    mockMvc
        .perform(post("/internal/pipeline/salary-reclassify").param("delayMs", "-1"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(post("/internal/pipeline/reclassify-jobkorea").param("delayMs", "-5"))
        .andExpect(status().isBadRequest());
  }
}
