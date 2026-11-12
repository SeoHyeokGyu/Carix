package com.carix.scenario.severance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.income.domain.IncomeChangeReasonEnum;
import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.profile.domain.RetirementPensionType;
import com.carix.scenario.dto.EvaluationResponse.SeverancePoint;
import com.carix.scenario.dto.EvaluationResponse.SeveranceView;
import com.carix.scenario.dto.ScenarioContextResponse;
import com.carix.scenario.inflation.InflationBasis;
import com.carix.scenario.tax.RetirementIncomeTax;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SeveranceOutlookServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
  private static final InflationBasis BASIS =
      new InflationBasis(new BigDecimal("2.0"), null, null, null, false, "2026-08", "한국은행");

  private IncomeHistoryRepository incomeRepository;
  private SeveranceOutlookService service;

  @BeforeEach
  void setUp() {
    incomeRepository = mock(IncomeHistoryRepository.class);
    service =
        new SeveranceOutlookService(
            incomeRepository,
            Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneId.of("Asia/Seoul")));
  }

  private void givenHistory(IncomeHistory... rows) {
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID)).willReturn(List.of(rows));
  }

  private static IncomeHistory row(long gross, LocalDate effective) {
    return new IncomeHistory(USER_ID, gross, null, effective, IncomeChangeReasonEnum.RAISE);
  }

  private static ScenarioContextResponse context(
      LocalDate hireDate, RetirementPensionType pensionType) {
    return new ScenarioContextResponse(
        60_000_000L, null, hireDate, pensionType, true, null, null, null, BASIS, null);
  }

  private static SeverancePoint point(SeveranceView view, String kind) {
    return view.points().stream().filter(p -> p.kind().equals(kind)).findFirst().orElseThrow();
  }

  @Test
  void 근속_1년_전이면_오늘은_0원이고_셋째_점은_1주년_마지막_근무일() {
    LocalDate hire = LocalDate.of(2026, 1, 10);
    givenHistory(row(60_000_000L, hire));

    SeveranceView view = service.outlook(USER_ID, context(hire, null), 0.03, null);

    assertThat(view.status()).isEqualTo("OK");
    assertThat(view.pensionType()).isEqualTo("UNKNOWN");
    // 1주년(2027-01-09)이 3개월 뒤(2027-02-12)보다 이르니 시간순으로 앞선다
    assertThat(view.points())
        .extracting(SeverancePoint::kind)
        .containsExactly("TODAY", "AFTER_ANNIVERSARY", "IN_3_MONTHS");
    SeverancePoint today = point(view, "TODAY");
    assertThat(today.gross()).isZero();
    assertThat(today.net()).isZero();
    SeverancePoint anniversary = point(view, "AFTER_ANNIVERSARY");
    assertThat(anniversary.lastWorkDay()).isEqualTo(LocalDate.of(2027, 1, 9));
    assertThat(anniversary.gross()).isPositive();
    assertThat(anniversary.deltaFromToday()).isEqualTo(anniversary.net());
  }

  @Test
  void 인상_예정일이_지났으면_다음_해로_민다() {
    LocalDate hire = LocalDate.of(2020, 3, 2);
    // 미래 적용분(2026-12-15)은 마지막 변경으로 보지 않는다 — 보면 인상일이 2027-12-15 로 밀린다
    givenHistory(
        row(90_000_000L, LocalDate.of(2026, 12, 15)),
        row(60_000_000L, LocalDate.of(2024, 1, 1)),
        row(55_000_000L, LocalDate.of(2023, 1, 1)));

    SeveranceView view =
        service.outlook(USER_ID, context(hire, RetirementPensionType.SEVERANCE_DB), 0.05, null);

    assertThat(view.pensionType()).isEqualTo("SEVERANCE_DB");
    assertThat(view.points())
        .extracting(SeverancePoint::kind)
        .containsExactly("TODAY", "IN_3_MONTHS", "AFTER_NEXT_RAISE");
    SeverancePoint raise = point(view, "AFTER_NEXT_RAISE");
    LocalDate lastWorkDay = LocalDate.of(2027, 2, 28);
    assertThat(raise.lastWorkDay()).isEqualTo(lastWorkDay);
    LocalDate raiseDate = LocalDate.of(2027, 1, 1);
    long expectedGross =
        SeveranceCalculator.grossSeverance(
            hire, lastWorkDay, d -> d.isBefore(raiseDate) ? 60_000_000L : 63_000_000L);
    assertThat(raise.gross()).isEqualTo(expectedGross);
    var tax = RetirementIncomeTax.of(expectedGross, hire, lastWorkDay);
    assertThat(raise.tax()).isEqualTo(tax.total());
    assertThat(raise.net()).isEqualTo(tax.net(expectedGross));
    assertThat(raise.deltaFromToday()).isEqualTo(raise.net() - point(view, "TODAY").net());
  }

  @Test
  void 오늘_퇴사는_그날_유효한_이력의_상여_포함_연봉으로_계산() {
    LocalDate hire = LocalDate.of(2022, 3, 2);
    IncomeHistory separateBonus =
        IncomeHistory.builder()
            .userId(USER_ID)
            .grossSalary(54_000_000L)
            .effectiveDate(LocalDate.of(2025, 10, 1))
            .bonusIncluded(false)
            .fixedBonusAnnual(6_000_000L)
            .build();
    givenHistory(row(66_000_000L, LocalDate.of(2026, 10, 1)), separateBonus);

    SeveranceView view = service.outlook(USER_ID, context(hire, null), 0.03, null);

    // 창(2026-08-13 ~ 11-12)의 첫 두 달은 직전 행(54,000,000 + 고정상여 6,000,000)이다
    long expected =
        SeveranceCalculator.grossSeverance(
            hire, TODAY, d -> d.isBefore(LocalDate.of(2026, 10, 1)) ? 60_000_000L : 66_000_000L);
    assertThat(point(view, "TODAY").gross()).isEqualTo(expected);
  }

  @Test
  void 가상_연봉이_있으면_이력_대신_그_연봉으로_계산() {
    LocalDate hire = LocalDate.of(2020, 3, 2);
    givenHistory(row(40_000_000L, LocalDate.of(2026, 10, 1)), row(30_000_000L, hire));

    SeveranceView view = service.outlook(USER_ID, context(hire, null), 0.03, 80_000_000L);

    assertThat(point(view, "TODAY").gross())
        .isEqualTo(SeveranceCalculator.grossSeverance(hire, TODAY, d -> 80_000_000L));
  }

  @Test
  void DC형이면_점_없이_DC_상태() {
    LocalDate hire = LocalDate.of(2020, 3, 2);
    givenHistory(row(60_000_000L, hire));

    SeveranceView view =
        service.outlook(USER_ID, context(hire, RetirementPensionType.DC), 0.03, null);

    assertThat(view.status()).isEqualTo("DC");
    assertThat(view.pensionType()).isEqualTo("DC");
    assertThat(view.points()).isEmpty();
  }

  @Test
  void 입사일이_없으면_NO_HIRE_DATE() {
    givenHistory(row(60_000_000L, LocalDate.of(2024, 1, 1)));

    SeveranceView view = service.outlook(USER_ID, context(null, null), 0.03, null);

    assertThat(view.status()).isEqualTo("NO_HIRE_DATE");
    assertThat(view.pensionType()).isEqualTo("UNKNOWN");
    assertThat(view.points()).isEmpty();
  }
}
