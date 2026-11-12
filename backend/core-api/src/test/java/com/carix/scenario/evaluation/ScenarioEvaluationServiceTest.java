package com.carix.scenario.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.scenario.ScenarioService;
import com.carix.scenario.UnemploymentBenefitAssumption;
import com.carix.scenario.dto.EvaluateRequest;
import com.carix.scenario.dto.EvaluationResponse;
import com.carix.scenario.dto.ScenarioContextResponse;
import com.carix.scenario.dto.SimulationResponse.FeasibilityDetail;
import com.carix.scenario.inflation.InflationAssumptions;
import com.carix.scenario.inflation.InflationBasis;
import com.carix.scenario.projection.StayMoveProjector;
import com.carix.scenario.tax.TakeHomeEstimator;
import com.carix.scenario.tax.TaxAssumption;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    service =
        new ScenarioEvaluationService(
            contextService,
            scenarioService,
            inflation,
            estimator,
            new StayMoveProjector(
                estimator, new UnemploymentBenefitAssumption(66_000L, 60_120L, "2026-01")),
            Clock.fixed(Instant.parse("2026-10-22T03:00:00Z"), ZoneId.of("Asia/Seoul")));
  }

  private void givenContext(LocalDate hireDate) {
    given(contextService.context(USER_ID))
        .willReturn(
            new ScenarioContextResponse(
                60_000_000L, null, hireDate, null, true, null, null, null, BASIS, null));
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
  void 입사_11개월차면_퇴직금_경고() {
    givenContext(LocalDate.parse("2025-11-22"));

    EvaluationResponse response =
        service.evaluate(USER_ID, new EvaluateRequest(stay(null, null), null));

    assertThat(response.severance().monthsToAnniversary()).isEqualTo(1);
    assertThat(response.severance().estimatedAmount()).isEqualTo(5_000_000L);
  }
}
