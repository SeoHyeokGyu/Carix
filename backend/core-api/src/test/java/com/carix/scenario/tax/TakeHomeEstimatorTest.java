package com.carix.scenario.tax;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TakeHomeEstimatorTest {

  private final TakeHomeEstimator estimator =
      new TakeHomeEstimator(
          new TaxAssumption("2026", 0.0475, 410_000, 6_590_000, 0.03595, 0.1314, 0.009));

  @Test
  void 연봉_6000만_세후는_공식_계산과_같다() {
    // 손계산 (월 500만, 연 6,000만):
    // 국민연금 5,000,000×4.75%×12 = 2,850,000
    // 건강 5,000,000×3.595%×12 = 2,157,000 / 장기요양 2,157,000×13.14% = 283,429.8 / 고용 540,000
    // 보험료 합 = 5,830,429.8
    // 근로소득공제 = 12,000,000 + (60,000,000-45,000,000)×5% = 12,750,000
    // 과세표준 = 60,000,000-12,750,000-1,500,000-2,850,000-2,980,429.8 = 39,919,570.2
    // 산출세액 = 39,919,570.2×15%-1,260,000 = 4,727,935.53
    // 근로소득세액공제 = min(715,000+30%×(4,727,935.53-1,300,000), max(660,000, 740,000-27,000,000×0.008))
    //                  = min(1,743,380.66, 660,000) = 660,000
    // 결정세액 = 4,727,935.53-660,000-130,000 = 3,937,935.53, 지방소득세 393,793.55
    // 세후 = 60,000,000-5,830,429.8-4,331,729.08 = 49,837,841.1
    assertThat(estimator.annualNet(60_000_000L)).isEqualTo(49_837_841L);
  }

  @Test
  void 국민연금은_상한에서_멈춘다() {
    // 연 1.2억: 월 1,000만이 상한 659만으로 잘려 국민연금 6,590,000×4.75%×12 = 3,756,300
    // 건강 4,314,000 / 장기요양 566,859.6 / 고용 1,080,000 → 보험료 합 9,717,159.6
    // 근로소득공제 = 14,750,000 + 20,000,000×2% = 15,150,000
    // 과세표준 = 120,000,000-15,150,000-1,500,000-3,756,300-5,960,859.6 = 93,632,840.4
    // 산출세액 = 93,632,840.4×35%-15,440,000 = 17,331,494.14, 세액공제 한도 500,000
    // 결정세액 = 17,331,494.14-500,000-130,000 = 16,701,494.14, 지방소득세 1,670,149.41
    // 세후 = 120,000,000-9,717,159.6-18,371,643.55 = 91,911,196.85
    assertThat(estimator.annualNet(120_000_000L)).isEqualTo(91_911_197L);
  }

  @Test
  void 보정비율_범위밖이면_적용하지_않는다() {
    long annualGross = 60_000_000L;
    long monthlyNet = Math.round(estimator.annualNet(annualGross) * 1.2 / 12);

    TakeHomeEstimator.Calibration calibration = estimator.calibrate(annualGross, monthlyNet);

    assertThat(calibration.applied()).isFalse();
    assertThat(calibration.ratio()).isEqualTo(1.0);
    assertThat(calibration.reasonCode()).isEqualTo("NET_CALIBRATION_SKIPPED");
  }
}
