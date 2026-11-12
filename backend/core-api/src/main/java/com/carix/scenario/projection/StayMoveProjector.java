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
    double[] staySeverance = stayRealSeverance(in, in.raiseRate());
    long[] stayCumulative = cumulative(stay, staySeverance);
    double stayChange = stayRealChangePercent(in);

    if (in.moveAnnualGross() == null) {
      return new ProjectionResult(stayChange, stayCumulative, null, null, null, null);
    }

    double[] move = moveRealMonthly(in);
    double[] moveSeverance = moveRealSeverance(in);
    long[] moveCumulative = cumulative(move, moveSeverance);

    long firstYearSeverance =
        Math.round(moveSeverance[FIRST_YEAR_MONTHS - 1])
            - Math.round(staySeverance[FIRST_YEAR_MONTHS - 1]);
    return new ProjectionResult(
        stayChange,
        stayCumulative,
        moveCumulative,
        crossover(stayCumulative, moveCumulative),
        firstYear(in, stayCumulative, moveCumulative, firstYearSeverance),
        equalizingRaisePercent(in, sum(move) + moveSeverance[HORIZON_MONTHS - 1]));
  }

  /** 잔류 1년차 세후 실질 변화가 0 이 되는 인상률(%). 구간 안에 해가 없거나 현재 세후가 0 이하면 null. */
  public Double breakevenRaisePercent(long currentAnnualGross, double inflationRate) {
    if (estimator.annualNet(currentAnnualGross) <= 0) {
      return null;
    }
    return solvePercent(
        BREAKEVEN_LOWER,
        BREAKEVEN_UPPER,
        rate -> realChange(currentAnnualGross, rate, inflationRate));
  }

  private double stayRealChangePercent(ProjectionInput in) {
    return realChange(in.currentAnnualGross(), in.raiseRate(), in.inflationRate()) * 100;
  }

  /** (인상 후 1년차 세후 ÷ (1+π)) ÷ 현재 세후 − 1. */
  private double realChange(long currentAnnualGross, double raiseRate, double inflationRate) {
    double current = estimator.annualNet(currentAnnualGross);
    double firstYear = estimator.annualNet(Math.round(currentAnnualGross * (1 + raiseRate)));
    return firstYear / (1 + inflationRate) / current - 1;
  }

  /** 잔류 월별 실질 세후. */
  private double[] stayRealMonthly(ProjectionInput in, double raiseRate) {
    double[] monthly = new double[HORIZON_MONTHS];
    for (int m = 1; m <= HORIZON_MONTHS; m++) {
      monthly[m - 1] = real(netMonthly(stayAnnualGross(m, in, raiseRate), in), m, in);
    }
    return monthly;
  }

  /** 잔류 m 개월째 연봉 = G₀ × (1+r)^(k+1). */
  private static double stayAnnualGross(int m, ProjectionInput in, double raiseRate) {
    int k = (m - 1) / MONTHS_PER_YEAR;
    return in.currentAnnualGross() * Math.pow(1 + raiseRate, k + 1);
  }

  /** 이직 m 개월째 연봉 = 이직 연봉 × (1+r)^k. */
  private static double moveAnnualGross(int m, ProjectionInput in) {
    int k = (m - 1) / MONTHS_PER_YEAR;
    return in.moveAnnualGross() * Math.pow(1 + in.raiseRate(), k);
  }

  /** 잔류 m 개월째 말에 나가면 받을 실질 세후 퇴직금 잔액. 반영 조건이 없으면 모두 0. */
  private static double[] stayRealSeverance(ProjectionInput in, double raiseRate) {
    double[] balances = new double[HORIZON_MONTHS];
    if (in.severance() == null) {
      return balances;
    }
    for (int m = 1; m <= HORIZON_MONTHS; m++) {
      balances[m - 1] = stayRealSeveranceAt(m, in, raiseRate);
    }
    return balances;
  }

  private static double stayRealSeveranceAt(int m, ProjectionInput in, double raiseRate) {
    long annualGross = Math.round(stayAnnualGross(m, in, raiseRate));
    return real(SeverancePath.stay(in.severance(), m, annualGross), m, in);
  }

  /**
   * 이직 m 개월째 말에 나가면 받을 실질 세후 퇴직금 잔액 = 오늘 받은 지금 회사분 + 새 회사분. 지금 회사분은 첫 달에 받으므로 한 번만 첫 달 물가로 할인한다.
   */
  private static double[] moveRealSeverance(ProjectionInput in) {
    double[] balances = new double[HORIZON_MONTHS];
    SeveranceTerms terms = in.severance();
    if (terms == null) {
      return balances;
    }
    double currentCompany =
        real(SeverancePath.currentCompany(terms, in.currentAnnualGross()), 1, in);
    for (int m = 1; m <= HORIZON_MONTHS; m++) {
      long annualGross = Math.round(moveAnnualGross(m, in));
      long newCompany = SeverancePath.newCompany(terms, in.unemploymentMonths(), m, annualGross);
      balances[m - 1] = currentCompany + real(newCompany, m, in);
    }
    return balances;
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
      ProjectionInput in, long[] stayCumulative, long[] moveCumulative, long severance) {
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
    long salaryDiff = total - roundedGap - roundedBenefit - cost - severance;
    return new FirstYear(salaryDiff, roundedGap, cost, roundedBenefit, severance, total);
  }

  /** moveTotal 은 이직 48개월 소득 합 + 48개월째 퇴직금. 잔류 쪽 퇴직금도 시험 인상률의 연봉 경로로 다시 계산한다. */
  private Double equalizingRaisePercent(ProjectionInput in, double moveTotal) {
    return solvePercent(
        EQUALIZING_LOWER,
        EQUALIZING_UPPER,
        rate -> sum(stayRealMonthly(in, rate)) + staySeveranceAtHorizon(in, rate) - moveTotal);
  }

  private static double staySeveranceAtHorizon(ProjectionInput in, double raiseRate) {
    return in.severance() == null ? 0 : stayRealSeveranceAt(HORIZON_MONTHS, in, raiseRate);
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
    return netMonthly(moveAnnualGross(m, in), in);
  }

  private double benefit(int m, ProjectionInput in) {
    if (!in.benefitEligible()
        || m > UnemploymentBenefitAssumption.benefitMonths(in.unemploymentMonths())) {
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

  /**
   * 월 소득은 쌓고, 퇴직금은 그 달에 나가면 받을 잔액이라 쌓지 않고 그 달 값에만 더한다. 둘을 따로 반올림해야 첫해 분해의 퇴직금 항이 누적 차이와 원 단위로 맞는다.
   */
  private static long[] cumulative(double[] monthly, double[] severance) {
    long[] result = new long[monthly.length];
    double running = 0;
    for (int i = 0; i < monthly.length; i++) {
      running += monthly[i];
      result[i] = Math.round(running) + Math.round(severance[i]);
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
