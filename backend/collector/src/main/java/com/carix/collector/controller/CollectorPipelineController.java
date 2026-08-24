package com.carix.collector.controller;

import com.carix.collector.dto.CrawlRequest;
import com.carix.collector.dto.JobExecutionResponse;
import com.carix.collector.dto.JobStatusResponse;
import com.carix.collector.dto.PipelineStatsResponse;
import com.carix.collector.dto.SeedLoadRequest;
import com.carix.collector.service.CollectorPipelineService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수집기(Collector) 내부 파이프라인 제어 컨트롤러.
 *
 * <p>API Gateway에 노출되지 않는 내부 전용(/internal/**) 엔드포인트입니다. 웹 스크래핑, 시드 적재, 원본 데이터 재가공 작업을 비동기로 트리거하고 진행
 * 상태 및 통계를 조회합니다.
 */
@RestController
@RequestMapping("/internal/pipeline")
public class CollectorPipelineController {

  private final CollectorPipelineService pipelineService;

  public CollectorPipelineController(CollectorPipelineService pipelineService) {
    this.pipelineService = pipelineService;
  }

  /**
   * 잡코리아 / 사람인 웹 스크래핑 크롤링 및 DB 직접 적재 비동기 트리거
   *
   * <p>[Input JSON Body 필드 명세]:
   *
   * <ul>
   *   <li><b>source</b> (String, optional): 수집 대상 포털. {@code "ALL"}(기본값), {@code "JOBKOREA"},
   *       {@code "SARAMIN"}
   *   <li><b>keyword</b> (String, optional): 잡코리아 검색 키워드. (기본값: {@code "IT개발"})
   *   <li><b>pageLimit</b> (Integer, optional): 수집할 최대 페이지 수. (기본값: {@code 5})
   *   <li><b>outputDir</b> (String, optional): 파일 산출물 디렉토리 경로 (미입력 시 기본 경로 자동 사용)
   * </ul>
   *
   * <pre>
   * [Input Body 예시 1 - 기본 전체 수집]:
   * {}
   *
   * [Input Body 예시 2 - 잡코리아만 10페이지 수집]:
   * {
   *   "source": "JOBKOREA",
   *   "keyword": "IT개발",
   *   "pageLimit": 10
   * }
   *
   * [Input Body 예시 3 - 사람인만 5페이지 수집]:
   * {
   *   "source": "SARAMIN",
   *   "pageLimit": 5
   * }
   * </pre>
   */
  @PostMapping("/crawl")
  public ResponseEntity<JobExecutionResponse> triggerCrawl(
      @RequestBody(required = false) CrawlRequest request) {
    CrawlRequest req = request != null ? request : new CrawlRequest(null, null, null, null);
    JobExecutionResponse response = pipelineService.triggerCrawl(req);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * 로컬 검증 산출물(verification/out) CSV/JSON 기반 DB 시드 적재 비동기 트리거
   *
   * <p>[Input JSON Body 필드 명세]:
   *
   * <ul>
   *   <li><b>dataDir</b> (String, optional): 시드 데이터 디렉토리 경로 (미입력 시 기본 {@code verification/out} 경로
   *       사용)
   * </ul>
   *
   * <pre>
   * [Input Body 예시 1 - 기본 경로 데이터 적재]:
   * {}
   *
   * [Input Body 예시 2 - 특정 디렉토리 지정 적재]:
   * {
   *   "dataDir": "/Users/yanghyojong/Projects/Carix/verification/out"
   * }
   * </pre>
   */
  @PostMapping("/seed")
  public ResponseEntity<JobExecutionResponse> triggerSeedLoad(
      @RequestBody(required = false) SeedLoadRequest request) {
    JobExecutionResponse response = pipelineService.triggerSeedLoad(request);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  /**
   * 특정 작업 진행 상태 및 결과 리포트 조회
   *
   * <p>[Path Variable]:
   *
   * <ul>
   *   <li><b>jobId</b> (String): 작업 생성 시 발급된 UUID (예: {@code
   *       "b490f845-8120-410a-b118-2e0619a9a3b8"})
   * </ul>
   */
  @GetMapping("/jobs/{jobId}")
  public ResponseEntity<JobStatusResponse> getJobStatus(@PathVariable String jobId) {
    return ResponseEntity.ok(pipelineService.getJobStatus(jobId));
  }

  /**
   * 최근 실행된 작업 히스토리 목록 조회 (최대 100건, 최신순)
   *
   * <p>별도의 Input 파라미터 없음.
   */
  @GetMapping("/jobs")
  public ResponseEntity<List<JobStatusResponse>> getRecentJobs() {
    return ResponseEntity.ok(pipelineService.getRecentJobs());
  }

  /**
   * 수집 DB 테이블(raw_ingest, job_posting, skill) 현황 통계 조회
   *
   * <p>별도의 Input 파라미터 없음.
   */
  @GetMapping("/stats")
  public ResponseEntity<PipelineStatsResponse> getStats() {
    return ResponseEntity.ok(pipelineService.getStats());
  }
}
