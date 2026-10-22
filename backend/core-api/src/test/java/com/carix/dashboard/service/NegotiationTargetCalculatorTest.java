package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.dashboard.dto.DashboardSummaryResponse.RecoveryTarget;
import com.carix.dashboard.dto.DashboardSummaryResponse.Target;
import com.carix.index.dto.WageBaseline;
import com.carix.peergroup.PeerSalaryDistribution;
import com.carix.snapshot.SalaryGapBucket;
import com.carix.wagestat.WageStatSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 협상 목표 인상률 세 줄 (기능 명세 US-D7·§4.8). */
class NegotiationTargetCalculatorTest {

  private static WageBaseline baseline(long median, WageStatSource source) {
    return new WageBaseline(median, "ANNUAL_GROSS_MEDIAN", "202606", source);
  }

  private static String bucketOf(long median, long salary) {
    return SalaryGapBucket.of(SalaryGapBucket.ratio(median, salary)).name();
  }

  /** 1천만 원 간격 공개연봉 10건(1천만~1억), 공고 수는 따로. */
  private static PeerSalaryDistribution peers(int disclosed, int peerSampleSize) {
    List<Long> salaries =
        LongStream.rangeClosed(1, disclosed).map(i -> i * 10_000_000L).boxed().toList();
    return new PeerSalaryDistribution(salaries, peerSampleSize, 0, "전국");
  }

  /** 스냅샷 판정도 KOSIS 기준선이었던 경우. */
  private static Target wage(long salary, WageBaseline baseline, String storedBucket) {
    return NegotiationTargetCalculator.wageLine(salary, baseline, storedBucket, "KOSIS");
  }

  @Test
  @DisplayName("임금통계 — 스냅샷 판정이 KOSIS 가 아닌 기준선이었으면 지운다 — 화면 출처 표기와 어긋난다")
  void wageLine_스냅샷출처_KOSIS아님_null() {
    WageBaseline now = baseline(60_000_000L, WageStatSource.KOSIS);
    String bucket = bucketOf(60_000_000L, 50_000_000L);

    assertThat(NegotiationTargetCalculator.wageLine(50_000_000L, now, bucket, "FALLBACK")).isNull();
    assertThat(NegotiationTargetCalculator.wageLine(50_000_000L, now, bucket, "UNKNOWN")).isNull();
    assertThat(NegotiationTargetCalculator.wageLine(50_000_000L, now, bucket, null)).isNull();
  }

  @Test
  @DisplayName("임금통계 — 중앙값보다 낮고 저장 구간과 맞으면 BELOW 와 인상률")
  void wageLine_중앙값아래_BELOW() {
    Target t =
        wage(
            50_000_000L,
            baseline(60_000_000L, WageStatSource.KOSIS),
            bucketOf(60_000_000L, 50_000_000L));

    assertThat(t).isEqualTo(new Target(20.0, "BELOW"));
  }

  @Test
  @DisplayName("임금통계 — 스냅샷 이후 기준선이 바뀌어 구간이 어긋나면 줄을 지운다")
  void wageLine_구간불일치_null() {
    assertThat(wage(50_000_000L, baseline(60_000_000L, WageStatSource.KOSIS), "NEAR_MARKET"))
        .isNull();
  }

  @Test
  @DisplayName("임금통계 — 저장 구간이 없으면 대조할 수 없어 지운다")
  void wageLine_저장구간없음_null() {
    assertThat(wage(50_000_000L, baseline(60_000_000L, WageStatSource.KOSIS), null)).isNull();
  }

  @Test
  @DisplayName("임금통계 — 기준선이 없으면 지운다")
  void wageLine_기준선없음_null() {
    assertThat(wage(50_000_000L, null, "BELOW_MARKET_15_PLUS")).isNull();
  }

  /** 방어용 — NEGOTIATE·SEEK_JOB 은 격차 5% 이상에서만 나와 구간 대조를 통과하면 운영에서는 나오지 않는다. */
  @Test
  @DisplayName("임금통계 — 이미 넘었으면 ABOVE, 인상률 없음")
  void wageLine_상회_ABOVE() {
    assertThat(
            wage(
                70_000_000L,
                baseline(60_000_000L, WageStatSource.KOSIS),
                bucketOf(60_000_000L, 70_000_000L)))
        .isEqualTo(new Target(null, "ABOVE"));
  }

  @Test
  @DisplayName("p75 — 공고·공개연봉 10건씩이면 nearest-rank p75 까지의 인상률")
  void peerLine_p75까지_BELOW() {
    // p75 = get(floor(10 × 0.75)) = 8천만
    assertThat(NegotiationTargetCalculator.peerLine(64_000_000L, peers(10, 10)))
        .isEqualTo(new Target(25.0, "BELOW"));
  }

  @Test
  @DisplayName("p75 — 반올림이 0.0 이면 +0.0% 가 아니라 ABOVE")
  void peerLine_반올림0_ABOVE() {
    assertThat(NegotiationTargetCalculator.peerLine(79_970_000L, peers(10, 10)))
        .isEqualTo(new Target(null, "ABOVE"));
  }

  @Test
  @DisplayName("p75 — 공개연봉 10건 미만이면 지운다 (c28)")
  void peerLine_공개연봉부족_null() {
    assertThat(NegotiationTargetCalculator.peerLine(64_000_000L, peers(9, 20))).isNull();
  }

  @Test
  @DisplayName("p75 — 동료집단 공고 10건 미만이면 지운다 (c28)")
  void peerLine_공고부족_null() {
    assertThat(NegotiationTargetCalculator.peerLine(64_000_000L, peers(10, 9))).isNull();
  }

  @Test
  @DisplayName("복구선 — 물가가 올랐으면 누적 상승률과 시작월")
  void recoveryLine_상승_BELOW() {
    assertThat(
            NegotiationTargetCalculator.recoveryLine(
                Optional.of(new BigDecimal("1.0434")), "202503"))
        .isEqualTo(new RecoveryTarget(4.3, "BELOW", "202503"));
  }

  @Test
  @DisplayName("복구선 — 물가가 내렸으면 NONE")
  void recoveryLine_하락_NONE() {
    assertThat(
            NegotiationTargetCalculator.recoveryLine(Optional.of(new BigDecimal("0.99")), "202503"))
        .isEqualTo(new RecoveryTarget(null, "NONE", "202503"));
  }

  @Test
  @DisplayName("복구선 — 개인물가를 계산할 수 없으면 지운다")
  void recoveryLine_결측_null() {
    assertThat(NegotiationTargetCalculator.recoveryLine(Optional.empty(), "202503")).isNull();
  }
}
