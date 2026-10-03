package com.carix.pipeline.controller;

import com.carix.pipeline.dto.CpiRequest;
import com.carix.pipeline.dto.CrawlRequest;
import com.carix.pipeline.dto.JobExecutionResponse;
import com.carix.pipeline.dto.JobStatusResponse;
import com.carix.pipeline.dto.MarketAggregateRequest;
import com.carix.pipeline.dto.PipelineStatsResponse;
import com.carix.pipeline.dto.SeedLoadRequest;
import com.carix.pipeline.dto.WageStatRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수집기 파이프라인 관리 및 비동기 배치 작업 제어 REST API 컨트롤러.
 *
 * <p>외부 채용 포털(잡코리아, 사람인) 크롤링, KOSIS 국가 공식 임금 통계 수집, 원천 간 공고 중복제거, 다차원 시장 데이터 사전 집계 등의 장기 실행 배치 작업을
 * 비동기로 트리거하고 상태를 모니터링합니다.
 */
@RestController
@RequestMapping("/internal/pipeline")
@RequiredArgsConstructor
public class CollectorPipelineController {

  private final CollectorPipelineService pipelineService;
  private final com.carix.pipeline.service.MviCollectionPriorityService priorityService;

  /**
   * [POST /internal/pipeline/crawl] 외부 채용 포털 실시간 크롤링 비동기 실행.
   *
   * <p>잡코리아, 사람인의 IT/개발 직무 채용공고 목록과 상세페이지를 실시간 스크래핑하여 {@code core.raw_ingest}에 원문을 보존하고 1차 정규화 및 스킬
   * 매핑을 수행합니다.
   *
   * @param request 크롤링 대상 포털(JOBKOREA, SARAMIN, ALL), 최대 페이지 수, 지연 시간 설정 (null 시 기본값)
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/crawl")
  public ResponseEntity<JobExecutionResponse> triggerCrawl(
      @RequestBody(required = false) CrawlRequest request) {
    JobExecutionResponse response = pipelineService.triggerCrawl(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * [POST /internal/pipeline/refine-skills] 크롤링된 채용공고 스킬 일괄 재정제 비동기 실행.
   *
   * <p>DB에 적재된 모든 채용공고의 제목과 기존 태그를 스킬 사전을 통해 다시 분석하여 스킬 매핑(job_posting_skill)을 최신화하고 불필요한 노이즈를
   * 제거합니다.
   *
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/refine-skills")
  public ResponseEntity<JobExecutionResponse> triggerSkillRefine() {
    JobExecutionResponse response = pipelineService.triggerSkillRefine();
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * [POST /internal/pipeline/seed-load] 파일 기반 스냅샷 시드 데이터 적재.
   *
   * <p>로컬/서버 파일시스템에 보존된 원문 JSON 파일들로부터 {@code core.raw_ingest}와 {@code core.job_posting} 테이블을 초기
   * 구축합니다. (초기 환경 세팅 또는 테스트 시 사용)
   *
   * @param request 시드 디렉토리 경로 및 대상 포털
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/seed-load")
  public ResponseEntity<JobExecutionResponse> triggerSeedLoad(
      @RequestBody(required = false) SeedLoadRequest request) {
    JobExecutionResponse response = pipelineService.triggerSeedLoad(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * [POST /internal/pipeline/wage-stat] KOSIS 국가 공식 임금 통계 수집 및 보간 적재.
   *
   * <p>통계청 OpenAPI(고용형태별근로실태조사 {@code DT_118N_PAYM39})를 호출하여 24개 임금 계층 분포를 수집하고, 누적분포 선형 보간을 통해
   * 연령/경력별 P50 중위연봉을 산출하여 {@code core.wage_stat_observation}에 Upsert 적재합니다. startYear/endYear를 지정하여
   * 과거 연도를 백필할 수 있습니다.
   *
   * @param request 수집 대상 연도 범위 (null 또는 미지정 시 최신 1개 연도)
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/wage-stat")
  public ResponseEntity<JobExecutionResponse> triggerWageStatIngest(
      @RequestBody(required = false) WageStatRequest request) {
    JobExecutionResponse response = pipelineService.triggerWageStatIngest(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * [POST /internal/pipeline/cpi] KOSIS 소비자물가지수 수집 및 적재.
   *
   * <p>통계청 OpenAPI(지출목적별 소비자물가지수 {@code DT_1J22001})를 호출하여 12대 분류 CPI를 수집하고 {@code
   * core.cpi_observation}에 적재합니다. startYearMonth/endYearMonth를 지정하여 과거 연월을 백필할 수 있습니다.
   *
   * @param request 수집 대상 연월 범위 (null 또는 미지정 시 최신 1개월)
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/cpi")
  public ResponseEntity<JobExecutionResponse> triggerCpiIngest(
      @RequestBody(required = false) CpiRequest request) {
    JobExecutionResponse response = pipelineService.triggerCpiIngest(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * [POST /internal/pipeline/dedup] 원천 간(잡코리아-사람인) 채용공고 중복제거 배치 실행.
   *
   * <p>전체 공고를 대상으로 회사명 정규화 + 제목 키워드 Jaccard 유사도 매칭을 수행하고, Union-Find 클러스터링을 통해 동일 공고 그룹에 동일한 {@code
   * dedup_group_id}와 {@code dedup_rule_version="v1"}을 부여합니다.
   *
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/dedup")
  public ResponseEntity<JobExecutionResponse> triggerDeduplication() {
    JobExecutionResponse response = pipelineService.triggerDeduplication();
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * [POST /internal/pipeline/aggregate] 채용 시장 데이터 다차원 사전 집계(Pre-aggregation) 실행.
   *
   * <p>단일 월, 연도 전체(1~12월), 연도 구간(startYear~endYear), 또는 전체 공고 기준월 일괄 집계를 지원합니다.
   *
   * @param request 집계 요청 옵션 (Body: month, year, startYear, endYear, all)
   * @param month 쿼리 파라미터 대상 년월 (하위 호환성 지원)
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/aggregate")
  public ResponseEntity<JobExecutionResponse> triggerMarketAggregation(
      @RequestBody(required = false) MarketAggregateRequest request,
      @RequestParam(value = "month", required = false) String month) {
    MarketAggregateRequest effectiveRequest =
        request != null
            ? request
            : (month != null
                ? new MarketAggregateRequest(month, null, null, null, null)
                : new MarketAggregateRequest(null, null, null, null, true));
    JobExecutionResponse response = pipelineService.triggerMarketAggregation(effectiveRequest);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * [GET /internal/pipeline/jobs/{jobId}] 비동기 작업 상태 및 실행 결과 단건 조회.
   *
   * @param jobId 비동기 트리거 시 발급받은 작업 고유 ID
   * @return 작업 상태(RUNNING, COMPLETED, FAILED), 시작/종료 시각, 상세 결과 통계
   */
  @GetMapping("/jobs/{jobId}")
  public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
    return ResponseEntity.ok(pipelineService.getJobStatus(jobId));
  }

  /**
   * [GET /internal/pipeline/jobs] 최근 비동기 파이프라인 작업 실행 이력 목록 조회.
   *
   * @return 최근 최대 100건의 작업 상태 목록 (최신순)
   */
  @GetMapping("/jobs")
  public ResponseEntity<List<JobStatusResponse>> getRecentJobs() {
    return ResponseEntity.ok(pipelineService.getRecentJobs());
  }

  /**
   * [GET /internal/pipeline/stats] 수집기 전체 DB 적재 현황 요약 통계 조회.
   *
   * @return raw_ingest 건수, job_posting 건수, skill 건수, wage_stat_observation 건수, 포털별 공고 수
   */
  @GetMapping("/stats")
  public ResponseEntity<PipelineStatsResponse> getStats() {
    return ResponseEntity.ok(pipelineService.getStats());
  }

  /**
   * [GET /internal/pipeline/recent-postings] 최근 수집/정규화된 채용공고 목록 조회 (최대 50건).
   *
   * @param limit 조회할 공고 건수 (기본값 20, 최대 50)
   * @return 최근 채용공고 목록 (스킬 태그 포함)
   */
  @GetMapping("/recent-postings")
  public ResponseEntity<List<com.carix.pipeline.dto.RecentPostingResponse>> getRecentPostings(
      @RequestParam(value = "limit", defaultValue = "20") int limit) {
    return ResponseEntity.ok(pipelineService.getRecentPostings(limit));
  }

  /**
   * [GET /internal/pipeline/priorities] MVI 시장 데이터 수집 우선순위 산출 결과 조회 (개발 계획 §2 b8b).
   *
   * @param month 기준 년월 (YYYYMM, null 시 최신 적재월 또는 당월)
   * @param source 원천 플랫폼 (ALL, JOBKOREA, SARAMIN, 기본값 ALL)
   * @param minSampleSize 최소 기준 표본수 (기본값 10)
   * @param underSampledOnly 표본 부족(N &lt; minSampleSize) 항목만 반환할지 여부 (기본값 false)
   * @return MVI 수집 우선순위 산출 결과
   */
  @GetMapping({"/priorities", "/collection-priorities"})
  public ResponseEntity<com.carix.pipeline.dto.CollectionPriorityResponse> getCollectionPriorities(
      @RequestParam(value = "month", required = false) String month,
      @RequestParam(value = "source", required = false, defaultValue = "ALL") String source,
      @RequestParam(value = "minSampleSize", required = false, defaultValue = "10")
          int minSampleSize,
      @RequestParam(value = "underSampledOnly", required = false, defaultValue = "false")
          boolean underSampledOnly) {
    return ResponseEntity.ok(
        priorityService.calculatePriorities(month, source, minSampleSize, underSampledOnly));
  }
}
