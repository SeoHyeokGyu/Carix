package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.CurrentSalary;
import com.carix.index.ExpenseWeightStatus;
import com.carix.index.IndexStatus;
import com.carix.index.MviResult;
import com.carix.index.PpiResult;
import com.carix.index.SourceCoverage;
import com.carix.index.WageBaseline;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DecisionSignalEngineTest {

  private static final String YM = "202607";
  private static final long SALARY = 60_000_000L;

  private final DecisionSignalEngine engine = new DecisionSignalEngine();

  /** 표본은 충분하고 기준선도 있는 정상 MVI. 중앙값으로 격차 비율을 조절한다. */
  private static MviResult mvi(double value, long medianGrossSalary) {
    return new MviResult(
        YM,
        BigDecimal.valueOf(value),
        IndexStatus.OK,
        List.of(new SourceCoverage("SARAMIN", 1, 40, "서울 전체 / 경력 4~7년", BigDecimal.valueOf(42.5))),
        List.of(),
        new WageBaseline(medianGrossSalary, "초과급여 제외, 상여 포함", "202606"),
        LocalDate.of(2026, 7, 1));
  }

  private static PpiResult ppi(double value) {
    return new PpiResult(
        YM,
        BigDecimal.valueOf(value),
        IndexStatus.OK,
        ExpenseWeightStatus.USER_DEFINED,
        "202601",
        LocalDate.of(2026, 7, 1));
  }

  private static CurrentSalary salary() {
    return new CurrentSalary(SALARY, LocalDate.of(2026, 1, 1));
  }

  // ---------- 규칙 2: 이직 탐색 ----------

  @Test
  @DisplayName("격차 0.15 이상 AND MVI 110 이상이면 이직 탐색이다 — 경계값 포함")
  void rule2_seekJob_atBoundary() {
    // 중앙값 69,000,000 → 격차 정확히 0.15
    DecisionOutcome outcome = engine.evaluate(ppi(100), mvi(110.0, 69_000_000L), salary());

    assertThat(outcome.code()).isEqualTo(DecisionSignalCode.SEEK_JOB);
    assertThat(outcome.salaryGapRatio()).isEqualByComparingTo("0.15");
  }

  @Test
  @DisplayName("MVI 가 110 미만이면 이직 탐색이 아니다")
  void rule2_notSeekJob_whenMviBelowThreshold() {
    // 격차 0.20 이지만 MVI 100 — 규칙 3 의 격차 상한(0.15)도 넘어 기본행으로 떨어진다
    DecisionOutcome outcome = engine.evaluate(ppi(100), mvi(100.0, 72_000_000L), salary());

    assertThat(outcome.code()).isEqualTo(DecisionSignalCode.STAY);
  }

  // ---------- 규칙 3: 협상 검토 ----------

  @Test
  @DisplayName("격차 0.05 이상 0.15 미만 AND MVI 105 이상이면 협상 검토다 — 하한 경계 포함")
  void rule3_negotiate_atLowerBoundary() {
    // 중앙값 63,000,000 → 격차 정확히 0.05
    DecisionOutcome outcome = engine.evaluate(ppi(100), mvi(105.0, 63_000_000L), salary());

    assertThat(outcome.code()).isEqualTo(DecisionSignalCode.NEGOTIATE);
  }

  @Test
  @DisplayName("격차가 0.15 이면 규칙 3 이 아니라 규칙 2 다 — 상한은 배타적이다")
  void rule3_upperBoundIsExclusive() {
    DecisionOutcome outcome = engine.evaluate(ppi(100), mvi(105.0, 69_000_000L), salary());

    // MVI 105 라 규칙 2(110 이상)에 못 미치고 규칙 3 의 격차 상한도 넘어 기본행이다
    assertThat(outcome.code()).isEqualTo(DecisionSignalCode.STAY);
  }

  // ---------- 규칙 4: 스킬 보강 우선 ----------

  @Test
  @DisplayName("MVI 90 미만 AND PPI 95 미만이면 스킬 보강 우선이다")
  void rule4_upskill() {
    DecisionOutcome outcome = engine.evaluate(ppi(94.9), mvi(89.9, 60_000_000L), salary());

    assertThat(outcome.code()).isEqualTo(DecisionSignalCode.UPSKILL);
  }

  @Test
  @DisplayName("PPI 가 95 면 스킬 보강 조건을 만족하지 않는다 — 경계는 배타적이다")
  void rule4_ppiBoundaryIsExclusive() {
    DecisionOutcome outcome = engine.evaluate(ppi(95.0), mvi(89.9, 60_000_000L), salary());

    assertThat(outcome.code()).isEqualTo(DecisionSignalCode.STAY);
  }

  // ---------- 규칙 5: 현 상태 유지 ----------

  @Test
  @DisplayName("격차가 -0.05 초과 0.05 미만이고 MVI 90 이상이면 현 상태 유지다")
  void rule5_stay() {
    DecisionOutcome outcome = engine.evaluate(ppi(100), mvi(90.0, 60_000_000L), salary());

    assertThat(outcome.code()).isEqualTo(DecisionSignalCode.STAY);
    assertThat(outcome.rulesApplied()).isNotEmpty();
  }

  // ---------- 데이터 부족 5조건 ----------

  @Test
  @DisplayName("데이터 부족 1 - 전 원천 합산 공고 수가 10 미만이면 데이터 부족이다")
  void insufficient1_sampleCountBelowTen() {
    MviResult scarce =
        new MviResult(
            YM,
            BigDecimal.valueOf(150),
            IndexStatus.OK,
            List.of(
                new SourceCoverage("SARAMIN", 5, 4, "서울 전체", BigDecimal.valueOf(80)),
                new SourceCoverage("JOBKOREA", 5, 5, "서울 전체", BigDecimal.valueOf(80))),
            List.of(),
            new WageBaseline(72_000_000L, "초과급여 제외, 상여 포함", "202606"),
            LocalDate.of(2026, 7, 1));

    assertThat(engine.evaluate(ppi(100), scarce, salary()).code())
        .isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("데이터 부족 2 - 계산 가능 스킬이 0개면 데이터 부족이다")
  void insufficient2_noComputableSkill() {
    MviResult noSkill =
        new MviResult(
            YM,
            null,
            IndexStatus.INSUFFICIENT_SAMPLE,
            List.of(new SourceCoverage("SARAMIN", 1, 40, "서울 전체", BigDecimal.valueOf(42.5))),
            List.of(),
            new WageBaseline(72_000_000L, "초과급여 제외, 상여 포함", "202606"),
            LocalDate.of(2026, 7, 1));

    assertThat(engine.evaluate(ppi(100), noSkill, salary()).code())
        .isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("데이터 부족 3 - 구매력 지수를 산출할 수 없으면 데이터 부족이다")
  void insufficient3_ppiNotAvailable() {
    assertThat(engine.evaluate(PpiResult.notAvailable(YM), mvi(120, 72_000_000L), salary()).code())
        .isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("데이터 부족 4 - 임금통계 기준선이 없으면 데이터 부족이다")
  void insufficient4_noWageBaseline() {
    MviResult noBaseline =
        new MviResult(
            YM,
            BigDecimal.valueOf(120),
            IndexStatus.OK,
            List.of(new SourceCoverage("SARAMIN", 1, 40, "서울 전체", BigDecimal.valueOf(42.5))),
            List.of(),
            null,
            LocalDate.of(2026, 7, 1));

    assertThat(engine.evaluate(ppi(100), noBaseline, salary()).code())
        .isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("데이터 부족 5 - 현재 연봉이 없으면 데이터 부족이다")
  void insufficient5_noCurrentSalary() {
    assertThat(engine.evaluate(ppi(100), mvi(120, 72_000_000L), null).code())
        .isEqualTo(DecisionSignalCode.INSUFFICIENT_DATA);
  }

  @Test
  @DisplayName("데이터 부족이면 격차 비율을 계산하지 않는다")
  void insufficientData_hasNoSalaryGapRatio() {
    assertThat(engine.evaluate(ppi(100), mvi(120, 72_000_000L), null).salaryGapRatio()).isNull();
  }

  @Test
  @DisplayName("INSUFFICIENT_HISTORY 는 값이 100 으로 존재하므로 데이터 부족이 아니다")
  void insufficientHistory_isNotInsufficientData() {
    PpiResult singlePoint =
        new PpiResult(
            YM,
            BigDecimal.valueOf(100),
            IndexStatus.INSUFFICIENT_HISTORY,
            ExpenseWeightStatus.USER_DEFINED,
            "202607",
            LocalDate.of(2026, 7, 1));

    assertThat(engine.evaluate(singlePoint, mvi(120.0, 72_000_000L), salary()).code())
        .isEqualTo(DecisionSignalCode.SEEK_JOB);
  }
}
