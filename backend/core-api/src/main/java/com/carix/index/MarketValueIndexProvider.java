package com.carix.index;

import com.carix.index.dto.MviResult;
import com.carix.index.enums.IndexStatus;
import java.util.UUID;

/**
 * 시장가치 지수 산출기. Member B 가 구현하고 Member C 의 스냅샷 엔진이 호출한다 (개발 계획 §4-1).
 *
 * <p>구현체가 없는 동안에는 {@code IndexProviderStubConfiguration} 의 스텁이 {@link IndexStatus#NOT_AVAILABLE} 을
 * 돌려준다. 실제 구현을 빈으로 등록하면 스텁은 물러난다.
 */
public interface MarketValueIndexProvider {

  /**
   * 특정 사용자의 특정 월 시장가치 지수를 산출한다.
   *
   * <p>계산 불가는 예외가 아니라 {@link IndexStatus} 로 돌려준다.
   *
   * @param userId 대상 사용자
   * @param baseYearMonth 기준년월. {@code YYYYMM} 6자
   */
  MviResult calculate(UUID userId, String baseYearMonth);
}
