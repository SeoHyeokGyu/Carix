package com.carix.dashboard.service;

import com.carix.dashboard.dto.DashboardSummaryResponse.RecoveryTarget;
import com.carix.dashboard.dto.DashboardSummaryResponse.Target;
import com.carix.index.dto.WageBaseline;
import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.snapshot.SalaryGapBucket;
import com.carix.wagestat.WageStatSource;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * 협상 목표 인상률 세 줄 (기능 명세 US-D7·§4.8). I/O 없이 재료만 받아 줄 하나씩 낸다. {@code null} 은 그 줄을 생략하라는 뜻이다.
 *
 * <p>필요 인상률 = (목표 − 현재 연봉) ÷ 현재 연봉. 소수 첫째 자리로 반올림해 0.0 이하면 이미 닿은 것으로 본다 — 「+0.0%」는 읽히지 않는다.
 */
final class NegotiationTargetCalculator {

  static final String BELOW = "BELOW";
  static final String ABOVE = "ABOVE";
  static final String NONE = "NONE";

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private NegotiationTargetCalculator() {}

  /**
   * 공식 임금통계 중앙값까지. KOSIS 실측 기준선만 쓴다 — 지금 기준선도, 스냅샷 판정 때 기준선도. 판정 때가 상수(FALLBACK)였으면 같은 화면의 출처
   * 표기({@code metadata.wageStat.source})와 이 줄의 출처가 어긋난다. 다시 구한 격차 구간이 스냅샷 판정 때의 구간과 다르면(그 뒤 임금통계가
   * 새로 적재됨) 판정과 모순되므로 지운다.
   */
  static Target wageLine(
      long currentGross, WageBaseline baseline, String storedGapBucket, String storedWageSource) {
    if (!WageStatSource.KOSIS.name().equals(storedWageSource)) {
      return null;
    }
    BigDecimal ratio = SalaryGapBucket.officialRatio(baseline, currentGross);
    if (ratio == null || !SalaryGapBucket.of(ratio).name().equals(storedGapBucket)) {
      return null;
    }
    return target(ratio);
  }

  /** 동료집단 공개연봉 75백분위까지. 공고·공개연봉 10건 규칙(c28)을 못 채우면 지운다. */
  static Target peerLine(long currentGross, PeerSalaryDistribution distribution) {
    if (distribution == null
        || !distribution.hasSufficientPeers()
        || !distribution.hasSufficientSalaries()) {
      return null;
    }
    BigDecimal ratio =
        BigDecimal.valueOf(distribution.p75() - currentGross)
            .divide(BigDecimal.valueOf(currentGross), MathContext.DECIMAL64);
    return target(ratio);
  }

  /**
   * 마지막 연봉 변경 이후 물가 상승분. 그 뒤 연봉이 그대로였으므로 필요 인상률이 곧 개인물가 누적 상승률이다.
   *
   * @param priceIndex 시작월 대비 개인물가 지수(1 기준). 계산할 수 없으면 empty
   */
  static RecoveryTarget recoveryLine(Optional<BigDecimal> priceIndex, String sinceYearMonth) {
    if (priceIndex.isEmpty()) {
      return null;
    }
    Double percent = positivePercent(priceIndex.get().subtract(BigDecimal.ONE));
    return percent == null
        ? new RecoveryTarget(null, NONE, sinceYearMonth)
        : new RecoveryTarget(percent, BELOW, sinceYearMonth);
  }

  private static Target target(BigDecimal ratio) {
    Double percent = positivePercent(ratio);
    return percent == null ? new Target(null, ABOVE) : new Target(percent, BELOW);
  }

  /** 반올림한 백분율이 0 보다 크면 그 값, 아니면 {@code null}. */
  private static Double positivePercent(BigDecimal ratio) {
    BigDecimal percent = ratio.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP);
    return percent.signum() > 0 ? percent.doubleValue() : null;
  }
}
