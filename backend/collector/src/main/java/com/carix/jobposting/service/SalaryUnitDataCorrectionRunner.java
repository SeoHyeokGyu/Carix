package com.carix.jobposting.service;

import com.carix.jobposting.JobPostingRepository;
import com.carix.market.MarketAggregateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 기존 DB에 만원 단위(예: 3000~15000)로 적재된 채용공고 및 시장 집계 연봉 데이터를 원 단위로 멱등하게 자동 보정하는 러너. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalaryUnitDataCorrectionRunner {

  private final JobPostingRepository jobPostingRepository;
  private final MarketAggregateRepository marketAggregateRepository;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void runDataCorrection() {
    try {
      int minUpdated = jobPostingRepository.normalizeSalaryMinUnits();
      int maxUpdated = jobPostingRepository.normalizeSalaryMaxUnits();
      if (minUpdated > 0 || maxUpdated > 0) {
        log.info(
            "[SalaryUnitCorrection] job_posting 연봉 단위 원(KRW) 보정 완료 (min: {}건, max: {}건)",
            minUpdated,
            maxUpdated);
      }
      int aggUpdated = marketAggregateRepository.normalizeSalaryUnits();
      if (aggUpdated > 0) {
        log.info("[SalaryUnitCorrection] market_aggregate 연봉 단위 원(KRW) 보정 완료 ({}건)", aggUpdated);
      }
    } catch (Exception e) {
      log.warn("[SalaryUnitCorrection] 연봉 단위 보정 중 알림 (테이블 미존재 또는 제약조건 등): {}", e.getMessage());
    }
  }
}
