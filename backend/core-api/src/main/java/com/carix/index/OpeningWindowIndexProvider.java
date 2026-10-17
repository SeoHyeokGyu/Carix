package com.carix.index;

import com.carix.index.dto.OwiResult;
import com.carix.index.enums.IndexStatus;
import java.util.UUID;

/**
 * 채용 활발도(OWI, Opening Window Index) 산출기. Member B 가 구현하고 Member C 의 스냅샷 엔진이 호출한다 (개발 계획 §3, 기능 명세
 * §4.5).
 *
 * <p>산출은 {@code core.market_aggregate} 월별 사전 집계 테이블을 조회하여 전체 시장 공고 대비 동료집단 공고의 채용 비중 추이(단기 비중 추세 +
 * 현재 비중 수준)를 계산한다.
 *
 * <p>구현체가 없는 동안에는 {@code IndexProviderStubConfiguration} 의 스텁이 {@link IndexStatus#NOT_AVAILABLE} 을
 * 돌려준다. 실제 구현을 빈으로 등록하면 스텁은 물러난다.
 */
public interface OpeningWindowIndexProvider {

  /**
   * 특정 사용자의 특정 월 채용 활발도(OWI) 지수를 산출한다.
   *
   * <p>계산 불가는 예외가 아니라 {@link IndexStatus} 로 돌려준다.
   *
   * @param userId 대상 사용자
   * @param baseYearMonth 기준년월. {@code YYYYMM} 6자
   */
  OwiResult calculate(UUID userId, String baseYearMonth);
}
