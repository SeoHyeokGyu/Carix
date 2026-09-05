package com.carix.cpi;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * KOSIS 지출목적별 소비자물가지수를 수집하여 {@code core.cpi_observation}에 적재하는 서비스.
 *
 * <p>카테고리는 KOSIS 12대 분류 코드(A~L) 원값 그대로 저장한다. Carix 6개 소비 카테고리로의 매핑·가중합성은 이 서비스의 책임이 아니며, Index
 * Engine이 {@code cpi-category-map.yml} / {@code default-expense-weights.yml} 기준으로 계산 시점에 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CpiIngestService {

  private final KosisCpiClient kosisClient;
  private final CpiObservationRepository repository;

  /** 최신 1개월 기준 CPI를 수집하여 적재합니다. */
  @Transactional
  public int ingestCpi() {
    return ingestCpi(null, null);
  }

  /**
   * 지정 연월 구간의 CPI를 수집하여 적재합니다.
   *
   * @param startYearMonth 수집 시작 연월 (예: "202401", null 시 최신 1개월)
   * @param endYearMonth 수집 종료 연월 (예: "202412", null 시 startYearMonth와 동일)
   * @return 저장된 관측치 행 수
   */
  @Transactional
  public int ingestCpi(String startYearMonth, String endYearMonth) {
    log.info(
        "[CPI수집] KOSIS 소비자물가지수 수집을 시작합니다... (기간: {} ~ {})",
        startYearMonth != null ? startYearMonth : "최신",
        endYearMonth != null ? endYearMonth : (startYearMonth != null ? startYearMonth : "최신"));

    Map<String, Map<String, BigDecimal>> monthlyData =
        kosisClient.fetchCpiByPeriod(startYearMonth, endYearMonth);

    int count = 0;
    for (Map.Entry<String, Map<String, BigDecimal>> monthEntry : monthlyData.entrySet()) {
      String baseYearMonth = monthEntry.getKey();
      for (Map.Entry<String, BigDecimal> categoryEntry : monthEntry.getValue().entrySet()) {
        saveIfAbsent(baseYearMonth, categoryEntry.getKey(), categoryEntry.getValue());
        count++;
      }
    }

    log.info("[CPI수집] KOSIS 소비자물가지수 수집 완료: 총 {}건의 관측치가 저장되었습니다.", count);
    return count;
  }

  private void saveIfAbsent(String baseYearMonth, String category, BigDecimal cpiValue) {
    Optional<CpiObservation> existing =
        repository.findByBaseYearMonthAndCategory(baseYearMonth, category);

    if (existing.isEmpty()) {
      CpiObservation observation =
          CpiObservation.builder()
              .baseYearMonth(baseYearMonth)
              .category(category)
              .cpiValue(cpiValue)
              .build();
      repository.save(observation);
    }
    // 기존 관측치가 있으면 그대로 둔다 (WageStatIngestService와 동일한 정책 —
    // 통계 개정(리베이싱) 대응은 별도 과제로 남겨둠).
  }
}
