package com.carix.scenario.inflation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.index.inflation.InflationComparison;
import com.carix.index.inflation.PersonalInflationService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 시뮬레이터 실질 환산에 쓰는 두 물가 기준: 한국은행 전망치, 전망치 + 개인물가 격차. */
class InflationAssumptionsTest {

  private static final UUID USER_ID = UUID.randomUUID();
  private static final String BASE = "2026-10";

  private PersonalInflationService personalInflationService;
  private InflationAssumptions assumptions;

  @BeforeEach
  void setUp() {
    personalInflationService = mock(PersonalInflationService.class);
    assumptions =
        new InflationAssumptions(
            new InflationForecastProperties(new BigDecimal("2.0"), "2026-08", "한국은행 경제전망"),
            personalInflationService);
  }

  private static InflationComparison comparison(String gapPp) {
    return new InflationComparison(
        "202609",
        new BigDecimal("3.00"),
        new BigDecimal("2.40"),
        new BigDecimal(gapPp),
        ExpenseCategoryEnum.HOUSING,
        new BigDecimal("0.40"));
  }

  @Test
  @DisplayName("개인물가는 정부전망에 차이를 더한다")
  void 개인물가는_정부전망에_차이를_더한다() {
    given(personalInflationService.trailing12m(USER_ID, BASE))
        .willReturn(Optional.of(comparison("0.60")));

    InflationBasis basis = assumptions.resolve(USER_ID, BASE);

    assertThat(basis.governmentPercent()).isEqualByComparingTo("2.0");
    assertThat(basis.personalPercent()).isEqualByComparingTo("2.6");
    assertThat(basis.topGapCategory()).isEqualTo(ExpenseCategoryEnum.HOUSING);
    assertThat(assumptions.effective(basis)).isEqualByComparingTo("2.6");
  }

  @Test
  @DisplayName("차이 0.2면 similar")
  void 차이_0점2면_similar() {
    given(personalInflationService.trailing12m(USER_ID, BASE))
        .willReturn(Optional.of(comparison("-0.20")));

    assertThat(assumptions.resolve(USER_ID, BASE).similar()).isTrue();
  }

  @Test
  @DisplayName("비교 불가면 정부전망으로")
  void 비교_불가면_정부전망으로() {
    given(personalInflationService.trailing12m(USER_ID, BASE)).willReturn(Optional.empty());

    InflationBasis basis = assumptions.resolve(USER_ID, BASE);

    assertThat(basis.personalPercent()).isNull();
    assertThat(assumptions.effective(basis)).isEqualByComparingTo("2.0");
  }
}
