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
   * [POST /internal/pipeline/reprocess] 보존된 원문(raw_ingest) 기반 공고 데이터 재가공(Backfill).
   *
   * <p>외부 사이트를 다시 긁지 않고, 이미 DB에 보존된 원문 HTML을 새로운 파싱/정규화 규칙(급여 범위, 직군 분류, 기업규모, Aho-Corasick 스킬 분류,
   * 중복제거 등)으로 재해석하여 {@code core.job_posting}을 최신화합니다.
   *
   * @param request dryRun 여부 (true일 경우 DB를 수정하지 않고 영향도만 시뮬레이션)
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/reprocess")
  public ResponseEntity<JobExecutionResponse> triggerReprocess(
      @RequestBody(required = false) ReprocessRequest request) {
    ReprocessRequest req = request != null ? request : new ReprocessRequest(true);
    JobExecutionResponse response = pipelineService.triggerReprocess(req);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * [POST /internal/pipeline/wage-stat] KOSIS 국가 공식 임금 통계 수집 및 보간 적재.
   *
   * <p>통계청 OpenAPI(고용형태별근로실태조사 {@code DT_118N_PAYM39})를 호출하여 24개 임금 계층 분포를 수집하고, 누적분포 선형 보간을 통해
   * 연령/경력별 P50 중위연봉을 산출하여 {@code core.wage_stat_observation}에 Upsert 적재합니다. (KOSIS 통계는 연간 발표되므로 연
   * 1회 또는 초기 구축 시 1회 실행 권장)
   *
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/wage-stat")
  public ResponseEntity<JobExecutionResponse> triggerWageStatIngest() {
    JobExecutionResponse response = pipelineService.triggerWageStatIngest();
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
   * [POST /internal/pipeline/aggregate] 월별 채용 시장 데이터 다차원 사전 집계(Pre-aggregation) 실행.
   *
   * <p>적재된 공고군(dedup_group_id 기준 통합 및 원천별)을 기반으로 직군 × 경력구간 × 지역 × 기업규모 × 스킬별 고유 공고 수, P25/P50/P75
   * 분위수 연봉, 결측률, 스킬 수요율을 일괄 계산하여 {@code core.market_aggregate}에 멱등하게 저장합니다. (매월 초 또는 신규 크롤링/백필 완료 후
   * 실행)
   *
   * @param month 집계 대상 년월 (형식: "YYYY-MM", 미입력 시 현재 월)
   * @return 202 Accepted 및 비동기 작업 추적용 jobId
   */
  @PostMapping("/aggregate")
  public ResponseEntity<JobExecutionResponse> triggerMarketAggregation(
      @RequestParam(value = "month", required = false) String month) {
    JobExecutionResponse response = pipelineService.triggerMarketAggregation(month);
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
}
