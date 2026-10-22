package com.carix.scenario.projection;

import com.carix.scenario.UnemploymentBenefitAssumption;
import com.carix.scenario.projection.ProjectionResult.FirstYear;
import com.carix.scenario.tax.TakeHomeEstimator;
import java.util.function.DoubleUnaryOperator;
import org.springframework.stereotype.Component;

/** 잔류·이직 48개월 세후·실질 월별 투영과 인사이트 (순수 계산). */
@Component
public class StayMoveProjector {

  public static final int HORIZON_MONTHS = 48;

  private static final int MONTHS_PER_YEAR = 12;
  private static final int FIRST_YEAR_MONTHS = 12;
  private static final int MAX_BENEFIT_MONTHS = 5;

  private static final double BREAKEVEN_LOWER = -0.5;
  private static final double BREAKEVEN_UPPER = 1.0;
  private static final double EQUALIZING_LOWER = -0.5;
  private static final double EQUALIZING_UPPER = 2.0;
  private static final double BISECTION_TOLERANCE = 0.0001;
  private static final int MAX_BISECTION_STEPS = 100;

  private final TakeHomeEstimator estimator;
  private final UnemploymentBenefitAssumption benefitAssumption;

  public StayMoveProjector(
      TakeHomeEstimator estimator, UnemploymentBenefitAssumption benefitAssumption) {
    this.estimator = estimator;
    this.benefitAssumption = benefitAssumption;
  }

  public ProjectionResult project(ProjectionInput in) {
    double[] stay = stayRealMonthly(in, in.raiseRate());
    long[] stayCumulative = cumulative(stay);
    double stayChange = stayRealChangePercent(in);

    if (in.moveAnnualGross() == null) {
      return new ProjectionResult(stayChange, stayCumulative, null, null, null, null);
    }

    double[] move = moveRealMonthly(in);
    long[] moveCumulative = cumulative(move);

    return new ProjectionResult(
        stayChange,
        stayCumulative,
        moveCumulative,
        crossover(stayCumulative, moveCumulative),
        firstYear(in, stay, stayCumulative, moveCumulative),
        equalizingRaisePercent(in, sum(move)));
  }

  /** 잔류 1년차 세후 실질 변화가 0 이 되는 인상률(%). 구간 안에 해가 없으면 null. */
  public Double breakevenRaisePercent(long currentAnnualGross, double inflationRate) {
    double current = estimator.annualNet(currentAnnualGross);
    return solvePercent(
        BREAKEVEN_LOWER,
        BREAKEVEN_UPPER,
        rate ->
            estimator.annualNet(Math.round(currentAnnualGross * (1 + rate)))
                    / (1 + inflationRate)
                    / current
                - 1);
  }

  private double stayRealChangePercent(ProjectionInput in) {
    double current = estimator.annualNet(in.currentAnnualGross());
    double firstYear =
        estimator.annualNet(Math.round(in.currentAnnualGross() * (1 + in.raiseRate())));
    return (firstYear / (1 + in.inflationRate()) / current - 1) * 100;
  }

  /** 잔류 월별 실질 세후. 연봉 = G₀ × (1+r)^(k+1). */
  private double[] stayRealMonthly(ProjectionInput in, double raiseRate) {
    double[] monthly = new double[HORIZON_MONTHS];
    for (int m = 1; m <= HORIZON_MONTHS; m++) {
      int k = (m - 1) / MONTHS_PER_YEAR;
      monthly[m - 1] =
          real(netMonthly(in.currentAnnualGross() * Math.pow(1 + raiseRate, k + 1), in), m, in);
    }
    return monthly;
  }

  /** 이직 월별 실질 세후. 공백 달은 급여 대신 실업급여, 첫 달에 일회성 비용을 뺀다. */
  private double[] moveRealMonthly(ProjectionInput in) {
    double[] monthly = new double[HORIZON_MONTHS];
    for (int m = 1; m <= HORIZON_MONTHS; m++) {
      double nominal = isGap(m, in) ? benefit(m, in) : movePay(m, in);
      if (m == 1) {
        nominal -= in.oneTimeCost();
      }
      monthly[m - 1] = real(nominal, m, in);
    }
    return monthly;
  }

  private FirstYear firstYear(
      ProjectionInput in, double[] stay, long[] stayCumulative, long[] moveCumulative) {
    double gap = 0;
    double benefit = 0;
    for (int m = 1; m <= FIRST_YEAR_MONTHS; m++) {
      if (isGap(m, in)) {
        gap -= real(movePay(m, in), m, in);
        benefit += real(benefit(m, in), m, in);
      }
    }
    long roundedGap = Math.round(gap);
    long roundedBenefit = Math.round(benefit);
    long cost = -Math.round(real(in.oneTimeCost(), 1, in));
    long total = moveCumulative[FIRST_YEAR_MONTHS - 1] - stayCumulative[FIRST_YEAR_MONTHS - 1];
    // 반올림 순서: 나머지 항을 먼저 반올림하고 급여 차이가 잔차를 흡수해 합이 total 과 정확히 같다
    long salaryDiff = total - roundedGap - roundedBenefit - cost;
    return new FirstYear(salaryDiff, roundedGap, cost, roundedBenefit, total);
  }

  private Double equalizingRaisePercent(ProjectionInput in, double moveTotal) {
    return solvePercent(
        EQUALIZING_LOWER, EQUALIZING_UPPER, rate -> sum(stayRealMonthly(in, rate)) - moveTotal);
  }

  /** 증가 함수 f 의 근을 이분법으로 찾아 %로 소수 첫째 자리까지 돌려준다. 구간 끝에서 부호가 같으면 null. */
  private static Double solvePercent(double lower, double upper, DoubleUnaryOperator f) {
    double lo = lower;
    double hi = upper;
    if (f.applyAsDouble(lo) > 0 || f.applyAsDouble(hi) < 0) {
      return null;
    }
    for (int i = 0; i < MAX_BISECTION_STEPS && hi - lo > BISECTION_TOLERANCE; i++) {
      double mid = (lo + hi) / 2;
      if (f.applyAsDouble(mid) < 0) {
        lo = mid;
      } else {
        hi = mid;
      }
    }
    return Math.round((lo + hi) / 2 * 1000) / 10.0;
  }

  private static Integer crossover(long[] stay, long[] move) {
    for (int i = 0; i < HORIZON_MONTHS; i++) {
      if (move[i] >= stay[i]) {
        return i + 1;
      }
    }
    return null;
  }

  private double movePay(int m, ProjectionInput in) {
    int k = (m - 1) / MONTHS_PER_YEAR;
    return netMonthly(in.moveAnnualGross() * Math.pow(1 + in.raiseRate(), k), in);
  }

  private double benefit(int m, ProjectionInput in) {
    if (!in.benefitEligible() || m > Math.min(in.unemploymentMonths(), MAX_BENEFIT_MONTHS)) {
      return 0;
    }
    return benefitAssumption.monthlyBenefit(in.currentAnnualGross());
  }

  private static boolean isGap(int m, ProjectionInput in) {
    return m <= in.unemploymentMonths();
  }

  private double netMonthly(double annualGross, ProjectionInput in) {
    return estimator.annualNet(Math.round(annualGross)) * in.calibrationRatio() / MONTHS_PER_YEAR;
  }

  private static double real(double nominal, int month, ProjectionInput in) {
    return nominal / Math.pow(1 + in.inflationRate(), (double) month / MONTHS_PER_YEAR);
  }

  private static long[] cumulative(double[] monthly) {
    long[] result = new long[monthly.length];
    double running = 0;
    for (int i = 0; i < monthly.length; i++) {
      running += monthly[i];
      result[i] = Math.round(running);
    }
    return result;
  }

  private static double sum(double[] values) {
    double total = 0;
    for (double value : values) {
      total += value;
    }
    return total;
  }
}
