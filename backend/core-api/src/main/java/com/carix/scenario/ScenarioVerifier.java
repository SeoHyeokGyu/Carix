package com.carix.scenario;

import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.index.dto.RecordedIncomeChange;
import com.carix.scenario.dto.ScenarioVerification;
import com.carix.scenario.dto.ScenarioVerification.Status;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 저장 시나리오의 입력 가정을 12개월 뒤 실제 소득과 맞댄다 (기능 명세 US-S1, c21). I/O 없이 재료만 받는다.
 *
 * <p>금액 예측이 아니라 세전 입력 가정을 맞댄다. 예측 결과는 세후·실질·누적이고 실제값은 세전 연봉뿐이라, 맞대면 예측 오차와 단위 차이가 구분되지 않는다.
 */
final class ScenarioVerifier {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final int WINDOW_MONTHS = 12;
  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private ScenarioVerifier() {}

  /**
   * @param offerAnnualGross 오퍼 세전 연 소득. 잔류만 저장한 시나리오면 {@code null}
   * @param incomes 사용자의 소득 행. 유효일 오름차순
   */
  static ScenarioVerification verify(
      double expectedRaisePercent,
      Long offerAnnualGross,
      OffsetDateTime calculatedAt,
      LocalDate today,
      List<RecordedIncomeChange> incomes) {
    LocalDate savedOn = calculatedAt.atZoneSameInstant(KST).toLocalDate();
    LocalDate dueOn = savedOn.plusMonths(WINDOW_MONTHS);
    if (today.isBefore(dueOn)) {
      return only(Status.WAITING, monthsUntil(today, dueOn));
    }

    List<RecordedIncomeChange> after =
        incomes.stream()
            .filter(
                row -> row.effectiveDate().isAfter(savedOn) && !row.effectiveDate().isAfter(dueOn))
            .toList();
    if (after.isEmpty()) {
      return only(Status.NO_INCOME_AFTER, null);
    }

    Optional<RecordedIncomeChange> jobChange =
        after.stream()
            .filter(row -> row.changeReason() == IncomeChangeReasonEnum.JOB_CHANGE)
            .findFirst();
    if (jobChange.isPresent()) {
      return offerAnnualGross == null
          ? only(Status.NO_OFFER, null)
          : new ScenarioVerification(
              Status.MOVE_COMPARED,
              null,
              null,
              null,
              offerAnnualGross,
              jobChange.get().annualGross());
    }

    Optional<RecordedIncomeChange> baseline =
        incomes.stream().filter(row -> !row.effectiveDate().isAfter(savedOn)).reduce((a, b) -> b);
    // 기준이 없으면 사유를 넣어도 비교할 수 없으니 사유 입력으로 보내지 않는다.
    if (baseline.isEmpty()) {
      return only(Status.NO_BASELINE, null);
    }
    if (after.stream().anyMatch(row -> row.changeReason() != IncomeChangeReasonEnum.RAISE)) {
      return only(Status.UNKNOWN_REASON, null);
    }
    return new ScenarioVerification(
        Status.STAY_COMPARED,
        null,
        // 화면이 실제와 같은 자리로 나란히 보이므로 서버에서 같은 규칙으로 반올림한다.
        BigDecimal.valueOf(expectedRaisePercent).setScale(1, RoundingMode.HALF_UP).doubleValue(),
        changePercent(after.getLast().annualGross(), baseline.get().annualGross()),
        null,
        null);
  }

  private static ScenarioVerification only(Status status, Integer monthsLeft) {
    return new ScenarioVerification(status, monthsLeft, null, null, null, null);
  }

  private static int monthsUntil(LocalDate today, LocalDate dueOn) {
    int months = (int) ChronoUnit.MONTHS.between(today, dueOn);
    return today.plusMonths(months).isBefore(dueOn) ? months + 1 : months;
  }

  private static double changePercent(long to, long from) {
    return BigDecimal.valueOf(to - from)
        .divide(BigDecimal.valueOf(from), MathContext.DECIMAL64)
        .multiply(HUNDRED)
        .setScale(1, RoundingMode.HALF_UP)
        .doubleValue();
  }
}
