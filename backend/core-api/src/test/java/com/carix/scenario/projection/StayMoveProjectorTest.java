package com.carix.scenario.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assertions.within;

import com.carix.scenario.UnemploymentBenefitAssumption;
import com.carix.scenario.projection.ProjectionResult.FirstYear;
import com.carix.scenario.tax.TakeHomeEstimator;
import com.carix.scenario.tax.TaxAssumption;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StayMoveProjectorTest {

  private static final double INFLATION = 0.026;
  private static final LocalDate TODAY = LocalDate.parse("2026-10-22");
  // 오늘 기준 근속 1년 미만이라 지금 회사분 퇴직금이 0이다
  private static final LocalDate SHORT_HIRE = LocalDate.parse("2026-03-02");

  private final StayMoveProjector projector =
      new StayMoveProjector(
          new TakeHomeEstimator(
              new TaxAssumption("2026", 0.0475, 410_000, 6_590_000, 0.03595, 0.1314, 0.009)),
          new UnemploymentBenefitAssumption(66_000L, 60_120L, "2026-01"));

  private ProjectionInput input(long moveGross, int gapMonths, long cost, boolean eligible) {
    return input(moveGross, gapMonths, cost, eligible, null);
  }

  private ProjectionInput input(
      long moveGross, int gapMonths, long cost, boolean eligible, SeveranceTerms severance) {
    return new ProjectionInput(
        60_000_000L, 0.03, moveGross, gapMonths, cost, eligible, INFLATION, 1.0, severance);
  }

  /** 퇴직금 반영 투영과 미반영 투영의 m 번째 달 이직 누적 증가분 차이 — 그 달에 더해진 퇴직금 잔액의 변화다. */
  private static long moveJump(ProjectionResult with, ProjectionResult without, int month) {
    int i = month - 1;
    return (with.moveCumulative()[i] - with.moveCumulative()[i - 1])
        - (without.moveCumulative()[i] - without.moveCumulative()[i - 1]);
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
        new ProjectionInput(60_000_000L, breakeven / 100, null, 0, 0L, false, INFLATION, 1.0, null);
    assertThat(projector.project(atBreakeven).stayRealChangePercent()).isCloseTo(0.0, offset(0.1));
  }

  @Test
  void 퇴직금이_연봉_차이에_섞이지_않는다() {
    SeveranceTerms terms = new SeveranceTerms(LocalDate.parse("2018-04-02"), TODAY);
    FirstYear without = projector.project(input(70_000_000L, 0, 0L, false)).firstYear();
    FirstYear with = projector.project(input(70_000_000L, 0, 0L, false, terms)).firstYear();

    assertThat(with.severance()).isNotZero();
    assertThat(without.severance()).isZero();
    assertThat(with.salaryDiff()).isEqualTo(without.salaryDiff());
    assertThat(with.total() - without.total()).isEqualTo(with.severance());
    assertThat(
            with.salaryDiff() + with.gap() + with.oneTimeCost() + with.benefit() + with.severance())
        .isEqualTo(with.total());
  }

  @Test
  void 새_회사_12개월째에_이직_쪽_퇴직금이_생긴다() {
    SeveranceTerms terms = new SeveranceTerms(SHORT_HIRE, TODAY);
    ProjectionResult without = projector.project(input(70_000_000L, 0, 0L, false));
    ProjectionResult with = projector.project(input(70_000_000L, 0, 0L, false, terms));

    // 지금 회사분 0, 새 회사는 1년 미만이라 11개월째까지 이직 쪽 퇴직금이 없다
    assertThat(with.moveCumulative()[10]).isEqualTo(without.moveCumulative()[10]);
    // 12개월째에 새 회사 1년치(연봉 7천만 ÷ 12 ≈ 583만, 세후·실질) 잔액이 생긴다
    long jump = moveJump(with, without, 12);
    assertThat(jump).isBetween(5_000_000L, 6_000_000L);
    // 잔액이지 흐름이 아니다: 13개월째 증가분은 한 달 근속분뿐이다
    assertThat(moveJump(with, without, 13)).isBetween(0L, jump / 6);
  }

  @Test
  void 쉬는_기간만큼_새_회사_퇴직금_시작이_밀린다() {
    SeveranceTerms terms = new SeveranceTerms(SHORT_HIRE, TODAY);
    ProjectionResult without = projector.project(input(70_000_000L, 2, 0L, false));
    ProjectionResult with = projector.project(input(70_000_000L, 2, 0L, false, terms));

    assertThat(with.moveCumulative()[12]).isEqualTo(without.moveCumulative()[12]);
    assertThat(moveJump(with, without, 14)).isBetween(5_000_000L, 6_000_000L);
  }

  @Test
  void severance_null_이면_기존_결과와_같다() {
    // 퇴직금 반영 전 코드로 같은 입력을 투영해 얻은 값이다
    ProjectionResult result = projector.project(input(70_000_000L, 2, 3_000_000L, true));

    assertThat(result.stayCumulative()[0]).isEqualTo(4_258_352L);
    assertThat(result.stayCumulative()[11]).isEqualTo(50_503_956L);
    assertThat(result.stayCumulative()[47]).isEqualTo(202_499_047L);
    assertThat(result.moveCumulative()[0]).isEqualTo(-1_017_821L);
    assertThat(result.moveCumulative()[11]).isEqualTo(48_075_048L);
    assertThat(result.moveCumulative()[47]).isEqualTo(217_633_398L);
    assertThat(result.crossoverMonth()).isEqualTo(17);
    assertThat(result.equalizingRaisePercent()).isEqualTo(6.4);
    assertThat(result.firstYear())
        .isEqualTo(
            new FirstYear(6_163_184L, -9_545_819L, -2_993_590L, 3_947_317L, 0L, -2_428_908L));
  }

  @Test
  void 퇴직금_계단으로_잠깐_앞서도_역전월은_끝까지_앞서기_시작한_달이다() {
    // 회귀: 첫 달은 연봉 차이로 앞서지만 2개월째 잔류 쪽이 근속 1년을 넘겨 퇴직금이 생기며 14개월째까지 뒤지는데,
    // 처음 앞선 달을 역전월로 골라 1이 나왔다
    LocalDate today = LocalDate.parse("2026-11-12");
    SeveranceTerms terms = new SeveranceTerms(LocalDate.parse("2026-01-10"), today);
    ProjectionResult result =
        projector.project(
            new ProjectionInput(
                60_000_000L, 0.03, 66_000_000L, 0, 0L, false, INFLATION, 1.0, terms));

    assertThat(result.moveCumulative()[0]).isGreaterThan(result.stayCumulative()[0]);
    assertThat(result.moveCumulative()[1]).isLessThan(result.stayCumulative()[1]);
    assertThat(result.crossoverMonth()).isGreaterThan(12);
    for (int i = result.crossoverMonth() - 1; i < StayMoveProjector.HORIZON_MONTHS; i++) {
      assertThat(result.moveCumulative()[i]).isGreaterThanOrEqualTo(result.stayCumulative()[i]);
    }
  }
}
