package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 소득 격차의 저장 형태 (기술 명세 §1.3).
 *
 * <p>비율을 소수점 6자리로 저장하면 평문 {@code wage_stat_observation.wage_median} · 평문 {@code career_profile}
 * 동료집단과 조인해 {@code 연봉 = 중앙값 / (1 + 격차비율)} 로 원 단위까지 복원된다 — {@code income_history} 의 AES-256-GCM 이
 * 막으려던 결과다. 구간으로 저장하면 복원해도 범위만 나온다.
 *
 * <p>경계는 결정 규칙표의 임계값(-0.05 / 0.05 / 0.15)과 같다. {@code rulesApplied} 가 이미 그 구간을 문구로 담고 있어 구간 저장이 정보를
 * 더 잃지도 않는다.
 */
class SalaryGapBucketTest {

  @Test
  @DisplayName("격차 15% 이상은 이직 탐색 구간이다")
  void 이직탐색_구간() {
    assertThat(SalaryGapBucket.of(new BigDecimal("0.150000")))
        .isEqualTo(SalaryGapBucket.BELOW_MARKET_15_PLUS);
    assertThat(SalaryGapBucket.of(new BigDecimal("0.203000")))
        .isEqualTo(SalaryGapBucket.BELOW_MARKET_15_PLUS);
  }

  @Test
  @DisplayName("격차 5~15% 는 협상 구간이다")
  void 협상_구간() {
    assertThat(SalaryGapBucket.of(new BigDecimal("0.050000")))
        .isEqualTo(SalaryGapBucket.BELOW_MARKET_5_TO_15);
    assertThat(SalaryGapBucket.of(new BigDecimal("0.149999")))
        .isEqualTo(SalaryGapBucket.BELOW_MARKET_5_TO_15);
  }

  @Test
  @DisplayName("격차 -5~5% 는 시장 수준이다")
  void 시장수준_구간() {
    assertThat(SalaryGapBucket.of(BigDecimal.ZERO)).isEqualTo(SalaryGapBucket.NEAR_MARKET);
    assertThat(SalaryGapBucket.of(new BigDecimal("-0.049999")))
        .isEqualTo(SalaryGapBucket.NEAR_MARKET);
  }

  @Test
  @DisplayName("연봉이 중앙값보다 5% 이상 높으면 시장 상회다")
  void 시장상회_구간() {
    assertThat(SalaryGapBucket.of(new BigDecimal("-0.050000")))
        .isEqualTo(SalaryGapBucket.ABOVE_MARKET_5_PLUS);
    assertThat(SalaryGapBucket.of(new BigDecimal("-0.300000")))
        .isEqualTo(SalaryGapBucket.ABOVE_MARKET_5_PLUS);
  }

  @Test
  @DisplayName("격차를 산출하지 못했으면 구간도 없다 — 0 이나 시장 수준으로 채우지 않는다")
  void 산출불가() {
    assertThat(SalaryGapBucket.of(null)).isNull();
  }

  @Test
  @DisplayName("구간만으로는 원 단위 연봉이 복원되지 않는다")
  void 복원_불가() {
    // 같은 구간에 서로 다른 연봉이 들어간다. 중앙값 7200만을 알아도 범위까지만 좁혀진다.
    SalaryGapBucket a = SalaryGapBucket.of(new BigDecimal("0.060000")); // 연봉 약 6792만
    SalaryGapBucket b = SalaryGapBucket.of(new BigDecimal("0.140000")); // 연봉 약 6316만

    assertThat(a).isEqualTo(b);
  }
}
