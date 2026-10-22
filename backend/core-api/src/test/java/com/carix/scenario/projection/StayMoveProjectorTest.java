package com.carix.scenario.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assertions.within;

import com.carix.scenario.UnemploymentBenefitAssumption;
import com.carix.scenario.projection.ProjectionResult.FirstYear;
import com.carix.scenario.tax.TakeHomeEstimator;
import com.carix.scenario.tax.TaxAssumption;
import org.junit.jupiter.api.Test;

class StayMoveProjectorTest {

  private static final double INFLATION = 0.026;

  private final StayMoveProjector projector =
      new StayMoveProjector(
          new TakeHomeEstimator(
              new TaxAssumption("2026", 0.0475, 410_000, 6_590_000, 0.03595, 0.1314, 0.009)),
          new UnemploymentBenefitAssumption(66_000L, 60_120L, "2026-01"));

  private ProjectionInput input(long moveGross, int gapMonths, long cost, boolean eligible) {
    return new ProjectionInput(
        60_000_000L, 0.03, moveGross, gapMonths, cost, eligible, INFLATION, 1.0);
  }

  @Test
  void 첫해_분해_합은_12개월_누적_차와_같다() {
    ProjectionResult result = projector.project(input(70_000_000L, 2, 3_000_000L, true));

    FirstYear first = result.firstYear();
    assertThat(first.total()).isEqualTo(result.moveCumulative()[11] - result.stayCumulative()[11]);
    assertThat(first.salaryDiff() + first.gap() + first.oneTimeCost() + first.benefit())
        .isEqualTo(first.total());
    // 공백·비용이 없는 같은 시나리오가 독립 기준이다 (잔차로 맞춰지는 합 검증만으로는 항 오류가 가려진다)
    ProjectionResult noGap = projector.project(input(70_000_000L, 0, 0L, true));
    assertThat(first.salaryDiff()).isCloseTo(noGap.firstYear().total(), within(5L));
    assertThat(first.gap()).isCloseTo(-noGap.moveCumulative()[1], within(5L));
    assertThat(first.gap()).isNegative();
    assertThat(first.oneTimeCost()).isNegative();
    assertThat(first.benefit()).isPositive();
  }

  @Test
  void 공백과_비용이_크면_첫해는_음수이고_역전월이_있다() {
    ProjectionResult result = projector.project(input(70_000_000L, 2, 3_000_000L, false));

    assertThat(result.firstYear().total()).isNegative();
    assertThat(result.crossoverMonth()).isGreaterThan(12).isLessThanOrEqualTo(48);
  }

  @Test
  void 이직_연봉이_낮으면_역전_없음() {
    ProjectionResult result = projector.project(input(55_000_000L, 1, 0L, false));

    assertThat(result.crossoverMonth()).isNull();
  }

  @Test
  void 손익분기_인상률에서_실질변화는_0이다() {
    Double breakeven = projector.breakevenRaisePercent(60_000_000L, INFLATION);

    ProjectionInput atBreakeven =
        new ProjectionInput(60_000_000L, breakeven / 100, null, 0, 0L, false, INFLATION, 1.0);
    assertThat(projector.project(atBreakeven).stayRealChangePercent()).isCloseTo(0.0, offset(0.1));
  }
}
