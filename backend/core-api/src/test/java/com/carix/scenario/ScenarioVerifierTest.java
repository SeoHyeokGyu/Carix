package com.carix.scenario;

import static com.carix.income.domain.IncomeChangeReasonEnum.INITIAL;
import static com.carix.income.domain.IncomeChangeReasonEnum.JOB_CHANGE;
import static com.carix.income.domain.IncomeChangeReasonEnum.RAISE;
import static org.assertj.core.api.Assertions.assertThat;

import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.index.dto.RecordedIncomeChange;
import com.carix.scenario.dto.ScenarioVerification;
import com.carix.scenario.dto.ScenarioVerification.Status;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScenarioVerifierTest {

  private static final OffsetDateTime SAVED = OffsetDateTime.parse("2025-10-15T10:00:00+09:00");
  private static final OffsetDateTime SAVED_RECENTLY =
      OffsetDateTime.parse("2026-08-06T10:00:00+09:00");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
  private static final Long OFFER = 72_000_000L;
  private static final RecordedIncomeChange BASE = row("2025-07-01", 55_000_000L, RAISE);

  private static RecordedIncomeChange row(
      String date, long annualGross, IncomeChangeReasonEnum reason) {
    return new RecordedIncomeChange(LocalDate.parse(date), annualGross, reason);
  }

  private static ScenarioVerification verify(RecordedIncomeChange... rows) {
    return ScenarioVerifier.verify(5.0, OFFER, SAVED, TODAY, List.of(rows));
  }

  @Test
  @DisplayName("전부 인상이면 저장 당시 대비 변화율을 예상과 나란히 낸다")
  void 잔류_비교() {
    ScenarioVerification result = verify(BASE, row("2026-03-01", 56_800_000L, RAISE));

    assertThat(result.status()).isEqualTo(Status.STAY_COMPARED);
    assertThat(result.expectedRaisePercent()).isEqualTo(5.0);
    assertThat(result.actualRaisePercent()).isEqualTo(3.3);
    assertThat(result.offerAnnualGross()).isNull();
  }

  @Test
  @DisplayName("예상 인상률도 실제와 같이 소수 첫째 자리로 반올림해 낸다")
  void 예상_인상률_반올림() {
    ScenarioVerification result =
        ScenarioVerifier.verify(
            1.15, OFFER, SAVED, TODAY, List.of(BASE, row("2026-03-01", 56_800_000L, RAISE)));

    assertThat(result.expectedRaisePercent()).isEqualTo(1.2);
  }

  @Test
  @DisplayName("인상이 여러 번이면 12개월 안 마지막 행까지 누적한다")
  void 여러_인상_누적() {
    assertThat(
            verify(
                    BASE,
                    row("2026-01-01", 56_000_000L, RAISE),
                    row("2026-06-01", 57_750_000L, RAISE))
                .actualRaisePercent())
        .isEqualTo(5.0);
  }

  @Test
  @DisplayName("연봉이 줄면 음수로 낸다")
  void 줄어든_연봉() {
    assertThat(verify(BASE, row("2026-03-01", 53_900_000L, RAISE)).actualRaisePercent())
        .isEqualTo(-2.0);
  }

  @Test
  @DisplayName("이직했으면 오퍼와 실제 연 소득을 낸다")
  void 이직_비교() {
    ScenarioVerification result = verify(BASE, row("2026-02-01", 69_000_000L, JOB_CHANGE));

    assertThat(result.status()).isEqualTo(Status.MOVE_COMPARED);
    assertThat(result.offerAnnualGross()).isEqualTo(72_000_000L);
    assertThat(result.actualAnnualGross()).isEqualTo(69_000_000L);
    assertThat(result.actualRaisePercent()).isNull();
  }

  @Test
  @DisplayName("이직 실제값은 첫 JOB_CHANGE 행이고 뒤 행은 보지 않는다")
  void 첫_이직_행() {
    ScenarioVerification result =
        verify(
            BASE,
            row("2026-02-01", 69_000_000L, JOB_CHANGE),
            row("2026-08-01", 71_000_000L, RAISE),
            row("2026-09-01", 73_000_000L, null));

    assertThat(result.status()).isEqualTo(Status.MOVE_COMPARED);
    assertThat(result.actualAnnualGross()).isEqualTo(69_000_000L);
  }

  @Test
  @DisplayName("이직했지만 시나리오에 오퍼가 없으면 비교하지 않는다")
  void 오퍼_없음() {
    ScenarioVerification result =
        ScenarioVerifier.verify(
            5.0, null, SAVED, TODAY, List.of(BASE, row("2026-02-01", 69_000_000L, JOB_CHANGE)));

    assertThat(result.status()).isEqualTo(Status.NO_OFFER);
  }

  @Test
  @DisplayName("저장 후 행이 없으면 동결인지 미입력인지 몰라 0% 로 내지 않는다")
  void 저장_후_행_없음() {
    ScenarioVerification result = verify(BASE);

    assertThat(result.status()).isEqualTo(Status.NO_INCOME_AFTER);
    assertThat(result.actualRaisePercent()).isNull();
  }

  @Test
  @DisplayName("소득 행이 전혀 없어도 예외 없이 저장 후 행 없음이다")
  void 소득_행_전혀_없음() {
    assertThat(verify().status()).isEqualTo(Status.NO_INCOME_AFTER);
  }

  @Test
  @DisplayName("사유 없는 행은 이직일 수 있어 잔류로 간주하지 않는다")
  void 사유_없는_행() {
    assertThat(verify(BASE, row("2026-03-01", 56_800_000L, null)).status())
        .isEqualTo(Status.UNKNOWN_REASON);
  }

  @Test
  @DisplayName("INITIAL 도 인상이 아니다 — RAISE·JOB_CHANGE 가 아닌 값은 잔류로 빠지지 않는다")
  void INITIAL_행() {
    assertThat(verify(BASE, row("2026-03-01", 56_800_000L, INITIAL)).status())
        .isEqualTo(Status.UNKNOWN_REASON);
  }

  @Test
  @DisplayName("인상 행에 사유 없는 행이 섞여도 비교하지 않는다")
  void 인상과_사유_없는_행() {
    assertThat(
            verify(
                    BASE,
                    row("2026-01-01", 56_000_000L, RAISE),
                    row("2026-03-01", 56_800_000L, null))
                .status())
        .isEqualTo(Status.UNKNOWN_REASON);
  }

  @Test
  @DisplayName("저장 당시 소득이 없으면 변화율의 기준이 없다")
  void 기준_행_없음() {
    assertThat(verify(row("2026-03-01", 56_800_000L, RAISE)).status())
        .isEqualTo(Status.NO_BASELINE);
  }

  @Test
  @DisplayName("기준 행이 없으면 사유를 넣어도 비교할 수 없으니 사유 입력으로 보내지 않는다")
  void 기준_없고_사유_없음() {
    assertThat(verify(row("2026-03-01", 56_800_000L, null)).status()).isEqualTo(Status.NO_BASELINE);
  }

  @Test
  @DisplayName("이직 비교는 기준 행이 필요 없다")
  void 기준_없이_이직() {
    assertThat(verify(row("2026-02-01", 69_000_000L, JOB_CHANGE)).status())
        .isEqualTo(Status.MOVE_COMPARED);
  }

  @Test
  @DisplayName("12개월 전이면 남은 개월을 낸다")
  void 대기() {
    ScenarioVerification result =
        ScenarioVerifier.verify(5.0, OFFER, SAVED_RECENTLY, TODAY, List.of(BASE));

    assertThat(result.status()).isEqualTo(Status.WAITING);
    assertThat(result.monthsLeft()).isEqualTo(9);
  }

  @Test
  @DisplayName("남은 개월은 올림이다")
  void 대기_올림() {
    assertThat(
            ScenarioVerifier.verify(
                    5.0, OFFER, SAVED_RECENTLY, LocalDate.of(2026, 11, 7), List.of(BASE))
                .monthsLeft())
        .isEqualTo(9);
  }

  @Test
  @DisplayName("12개월 전에 이직해도 만기까지는 대기다")
  void 대기_중_이직() {
    assertThat(
            ScenarioVerifier.verify(
                    5.0,
                    OFFER,
                    SAVED_RECENTLY,
                    TODAY,
                    List.of(BASE, row("2026-10-01", 69_000_000L, JOB_CHANGE)))
                .status())
        .isEqualTo(Status.WAITING);
  }

  @Test
  @DisplayName("만기 전날은 대기, 만기 당일은 비교한다")
  void 만기_경계() {
    ScenarioVerification dayBefore =
        ScenarioVerifier.verify(5.0, OFFER, SAVED, LocalDate.of(2026, 10, 14), List.of(BASE));
    ScenarioVerification dueDay =
        ScenarioVerifier.verify(5.0, OFFER, SAVED, LocalDate.of(2026, 10, 15), List.of(BASE));

    assertThat(dayBefore.status()).isEqualTo(Status.WAITING);
    assertThat(dayBefore.monthsLeft()).isEqualTo(1);
    assertThat(dueDay.status()).isEqualTo(Status.NO_INCOME_AFTER);
  }

  @Test
  @DisplayName("만기 당일 행은 쓰고 그 뒤 행은 무시한다")
  void 창_끝() {
    ScenarioVerification result =
        verify(BASE, row("2026-10-15", 56_100_000L, RAISE), row("2026-10-16", 66_000_000L, RAISE));

    assertThat(result.actualRaisePercent()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("저장일은 한국 날짜다 — UTC 로 전날이어도 그날 행은 저장 후가 아니라 기준이다")
  void 저장일_한국_날짜() {
    ScenarioVerification result =
        ScenarioVerifier.verify(
            5.0,
            OFFER,
            OffsetDateTime.parse("2025-10-14T15:00:00Z"),
            TODAY,
            List.of(row("2025-10-15", 50_000_000L, RAISE), row("2026-03-01", 51_000_000L, RAISE)));

    assertThat(result.status()).isEqualTo(Status.STAY_COMPARED);
    assertThat(result.actualRaisePercent()).isEqualTo(2.0);
  }

  @Test
  @DisplayName("2월 29일에 저장해도 만기가 다음 해 2월 28일로 잡힌다")
  void 윤일_저장() {
    ScenarioVerification result =
        ScenarioVerifier.verify(
            5.0,
            OFFER,
            OffsetDateTime.parse("2024-02-29T10:00:00+09:00"),
            LocalDate.of(2025, 2, 28),
            List.of());

    assertThat(result.status()).isEqualTo(Status.NO_INCOME_AFTER);
  }
}
