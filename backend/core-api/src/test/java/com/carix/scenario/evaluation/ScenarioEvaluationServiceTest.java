package com.carix.scenario.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.income.domain.IncomeHistory;
import com.carix.income.repository.IncomeHistoryRepository;
import com.carix.profile.domain.RetirementPensionType;
import com.carix.scenario.ScenarioService;
import com.carix.scenario.UnemploymentBenefitAssumption;
import com.carix.scenario.dto.EvaluateRequest;
import com.carix.scenario.dto.EvaluationResponse;
import com.carix.scenario.dto.EvaluationResponse.Assumption;
import com.carix.scenario.dto.ScenarioContextResponse;
import com.carix.scenario.dto.SimulationResponse.FeasibilityDetail;
import com.carix.scenario.inflation.InflationAssumptions;
import com.carix.scenario.inflation.InflationBasis;
import com.carix.scenario.projection.StayMoveProjector;
import com.carix.scenario.severance.SeveranceOutlookService;
import com.carix.scenario.tax.TakeHomeEstimator;
import com.carix.scenario.tax.TaxAssumption;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioEvaluationServiceTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final InflationBasis BASIS =
      new InflationBasis(new BigDecimal("2.0"), null, null, null, false, "2026-08", "한국은행");

  private ScenarioContextService contextService;
  private ScenarioEvaluationService service;

  @BeforeEach
  void setUp() {
    contextService = mock(ScenarioContextService.class);
    ScenarioService scenarioService = mock(ScenarioService.class);
    given(scenarioService.feasibilityDetail(any(), anyLong()))
        .willReturn(new FeasibilityDetail("INSUFFICIENT_SAMPLE", 0, 10, null, null, null, null));
    TakeHomeEstimator estimator =
        new TakeHomeEstimator(
            new TaxAssumption("2026", 0.0475, 410_000, 6_590_000, 0.03595, 0.1314, 0.009));
    InflationAssumptions inflation = mock(InflationAssumptions.class);
    given(inflation.effective(BASIS)).willReturn(new BigDecimal("2.0"));
    IncomeHistoryRepository incomeRepository = mock(IncomeHistoryRepository.class);
    given(incomeRepository.findByUserIdOrderByEffectiveDateDesc(USER_ID))
        .willReturn(
            List.of(
                new IncomeHistory(USER_ID, 60_000_000L, null, LocalDate.of(2025, 11, 22), null)));
    Clock clock = Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    service =
        new ScenarioEvaluationService(
            contextService,
            scenarioService,
            inflation,
            estimator,
            new StayMoveProjector(
                estimator, new UnemploymentBenefitAssumption(66_000L, 60_120L, "2026-01")),
            new SeveranceOutlookService(incomeRepository, clock),
            clock);
  }

  private void givenContext(LocalDate hireDate) {
    givenContext(hireDate, null);
  }

  private void givenContext(LocalDate hireDate, RetirementPensionType pensionType) {
    given(contextService.context(USER_ID))
        .willReturn(
            new ScenarioContextResponse(
                60_000_000L, null, hireDate, pensionType, true, null, null, null, BASIS, null));
  }

  private static EvaluateRequest.Stay stay(Long stockVesting, Long stockUnvested) {
    return new EvaluateRequest.Stay(3.0, null, null, stockVesting, stockUnvested);
  }

  private static EvaluateRequest.Move move(Long stockVesting) {
    return new EvaluateRequest.Move(
        70_000_000L, 2, 3_000_000L, true, true, null, null, null, stockVesting);
  }

  @Test
  void 주식_입력_유무로_firstYear_total이_같다() {
    givenContext(null);

    EvaluationResponse without =
        service.evaluate(USER_ID, new EvaluateRequest(stay(null, null), move(null)));
    EvaluationResponse with =
        service.evaluate(
            USER_ID, new EvaluateRequest(stay(8_000_000L, 20_000_000L), move(15_000_000L)));

    assertThat(with.move().firstYear().total()).isEqualTo(without.move().firstYear().total());
    assertThat(with.move().stock()).isNotNull();
    assertThat(without.move().stock()).isNull();
  }

  @Test
  void 입사일이_있으면_퇴직금_블록과_그_가정을_낸다() {
    givenContext(LocalDate.parse("2025-11-22"));

    EvaluationResponse response =
        service.evaluate(USER_ID, new EvaluateRequest(stay(null, null), move(null)));

    // 옛 「N개월만 더 다니면」 경고 대신 세 시점 블록이다. 1주년 전이라 오늘 퇴사는 0원
    assertThat(response.severance().status()).isEqualTo("OK");
    assertThat(response.severance().points()).hasSize(3);
    assertThat(response.severance().points().get(0).gross()).isZero();
    assertThat(response.assumptions())
        .extracting(Assumption::code)
        .contains("SEVERANCE_BASIS", "SEVERANCE_IRP", "SEVERANCE_ASSUMED_DB")
        .doesNotContain("SEVERANCE_ESTIMATE");
    assertThat(response.assumptions())
        .filteredOn(a -> a.code().equals("SEVERANCE_TAX_AS_OF"))
        .extracting(Assumption::text)
        .containsExactly("퇴직소득세는 2026년 세법 기준입니다.");
    assertThat(response.assumptions())
        .filteredOn(a -> a.code().equals("NEW_JOB_SEVERANCE"))
        .extracting(Assumption::text)
        .containsExactly("퇴직금은 양쪽 모두 그 달에 나간다고 보고 넣었습니다");
  }

  @Test
  void 입사일이_없으면_퇴직금_가정_없이_미반영에_입사일() {
    givenContext(null);

    EvaluationResponse response =
        service.evaluate(USER_ID, new EvaluateRequest(stay(null, null), move(null)));

    assertThat(response.severance().status()).isEqualTo("NO_HIRE_DATE");
    assertThat(response.assumptions())
        .extracting(Assumption::code)
        .doesNotContain("SEVERANCE_BASIS", "SEVERANCE_IRP", "SEVERANCE_ASSUMED_DB");
    assertThat(response.unreflected()).contains("HIRE_DATE");
  }

  @Test
  void DC형이면_퇴직금_가정을_넣지_않는다() {
    givenContext(LocalDate.parse("2020-03-02"), RetirementPensionType.DC);

    EvaluationResponse response =
        service.evaluate(USER_ID, new EvaluateRequest(stay(null, null), null));

    assertThat(response.severance().status()).isEqualTo("DC");
    assertThat(response.assumptions())
        .extracting(Assumption::code)
        .doesNotContain("SEVERANCE_BASIS", "SEVERANCE_IRP", "SEVERANCE_TAX_AS_OF");
  }

  @Test
  void 근속이_길어도_유형_라벨은_퇴직금을_빼고_정한다() {
    givenContext(LocalDate.parse("2010-03-02"));
    // 오퍼가 조금 낮아 급여는 손해지만, 퇴직금이 두 번에 나뉘어 세금이 줄어 퇴직금 차이가 그 손해보다 크다
    EvaluateRequest request =
        new EvaluateRequest(
            new EvaluateRequest.Stay(0.0, null, null, null, null),
            new EvaluateRequest.Move(59_000_000L, 0, 0L, false, true, null, null, null, null));

    EvaluationResponse response = service.evaluate(USER_ID, request);

    var firstYear = response.move().firstYear();
    assertThat(firstYear.total()).isNotNegative();
    assertThat(firstYear.total() - firstYear.severance()).isNegative();
    assertThat(response.typeLabel()).isEqualTo("남으면 추정 이득");
  }
}
