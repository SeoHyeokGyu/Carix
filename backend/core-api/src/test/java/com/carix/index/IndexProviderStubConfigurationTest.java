package com.carix.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.stub.IndexProviderStubConfiguration;
import com.carix.index.stub.StubMarketValueIndexProvider;
import com.carix.index.stub.StubPurchasingPowerIndexProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class IndexProviderStubConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(IndexProviderStubConfiguration.class));

  @Test
  void 구현이_없으면_스텁이_등록된다() {
    runner.run(
        context -> {
          assertThat(context.getBean(PurchasingPowerIndexProvider.class))
              .isInstanceOf(StubPurchasingPowerIndexProvider.class);
          assertThat(context.getBean(MarketValueIndexProvider.class))
              .isInstanceOf(StubMarketValueIndexProvider.class);
        });
  }

  @Test
  void 실제_구현이_있으면_스텁은_물러난다() {
    runner
        .withUserConfiguration(RealProviders.class)
        .run(
            context -> {
              assertThat(context.getBean(PurchasingPowerIndexProvider.class))
                  .isNotInstanceOf(StubPurchasingPowerIndexProvider.class);
              assertThat(context.getBean(MarketValueIndexProvider.class))
                  .isNotInstanceOf(StubMarketValueIndexProvider.class);
            });
  }

  @Configuration
  static class RealProviders {

    @Bean
    PurchasingPowerIndexProvider realPpi() {
      return (userId, baseYearMonth) ->
          new PpiResult(
              baseYearMonth,
              new BigDecimal("94.20"),
              IndexStatus.OK,
              ExpenseWeightStatus.USER_DEFINED,
              "202601",
              LocalDate.of(2026, 7, 1));
    }

    @Bean
    MarketValueIndexProvider realMvi() {
      return (userId, baseYearMonth) ->
          new MviResult(
              baseYearMonth,
              new BigDecimal("117.50"),
              IndexStatus.OK,
              List.of(
                  new SourceCoverage("SARAMIN", 3, 124, "서울 전체 / MID", new BigDecimal("42.50"))),
              List.of(),
              new WageBaseline(72_000_000L, "초과급여 제외, 상여 포함", "202606"),
              LocalDate.of(2026, 7, 1));
    }
  }

  @Test
  void 스텁은_숫자를_지어내지_않는다() {
    runner.run(
        context -> {
          UUID userId = UUID.randomUUID();
          assertThat(
                  context.getBean(PurchasingPowerIndexProvider.class).calculate(userId, "202608"))
              .extracting(PpiResult::value, PpiResult::status)
              .containsExactly(null, IndexStatus.NOT_AVAILABLE);
          assertThat(context.getBean(MarketValueIndexProvider.class).calculate(userId, "202608"))
              .extracting(MviResult::value, MviResult::status)
              .containsExactly(null, IndexStatus.NOT_AVAILABLE);
        });
  }
}
