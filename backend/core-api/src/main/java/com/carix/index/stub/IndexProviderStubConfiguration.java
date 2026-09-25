package com.carix.index.stub;

import com.carix.index.CurrentSalaryProvider;
import com.carix.index.MarketValueIndexProvider;
import com.carix.index.OpeningWindowIndexProvider;
import com.carix.index.PurchasingPowerIndexProvider;
import com.carix.index.WageStatSeriesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 산출기 구현이 아직 없을 때만 스텁을 등록한다.
 *
 * <p>스텁 클래스에 {@code @Component} 와 {@code @ConditionalOnMissingBean} 을 직접 붙이지 않는다 — 컴포넌트 스캔 순서에 따라
 * 조건이 평가돼 실제 구현이 있어도 스텁이 남을 수 있다. {@code @Configuration} 의 {@code @Bean} 메서드는 사용자 빈이 등록된 뒤에 평가된다.
 */
@Configuration
public class IndexProviderStubConfiguration {

  private static final Logger log = LoggerFactory.getLogger(IndexProviderStubConfiguration.class);

  @Bean
  @ConditionalOnMissingBean(PurchasingPowerIndexProvider.class)
  PurchasingPowerIndexProvider stubPurchasingPowerIndexProvider() {
    log.info("[지수계약] 구매력 지수(PPI) 산출기가 아직 스텁입니다. 모든 요청이 NOT_AVAILABLE 을 돌려줍니다.");
    return new StubPurchasingPowerIndexProvider();
  }

  @Bean
  @ConditionalOnMissingBean(MarketValueIndexProvider.class)
  MarketValueIndexProvider stubMarketValueIndexProvider() {
    log.info("[지수계약] 시장가치 지수(MVI) 산출기가 아직 스텁입니다. 모든 요청이 NOT_AVAILABLE 을 돌려줍니다.");
    return new StubMarketValueIndexProvider();
  }

  @Bean
  @ConditionalOnMissingBean(OpeningWindowIndexProvider.class)
  OpeningWindowIndexProvider stubOpeningWindowIndexProvider() {
    log.info("[지수계약] 개방창 지수(OWI) 산출기가 아직 스텁입니다. 모든 요청이 NOT_AVAILABLE 을 돌려줍니다.");
    return new StubOpeningWindowIndexProvider();
  }

  @Bean
  @ConditionalOnMissingBean(CurrentSalaryProvider.class)
  CurrentSalaryProvider stubCurrentSalaryProvider() {
    log.info("[지수계약] 현재 연봉 제공자가 아직 스텁입니다. 모든 요청이 빈 결과를 돌려줍니다.");
    return new StubCurrentSalaryProvider();
  }

  @Bean
  @ConditionalOnMissingBean(WageStatSeriesProvider.class)
  WageStatSeriesProvider stubWageStatSeriesProvider() {
    log.info("[지수계약] 공식 임금통계 연도 시계열 제공자가 아직 스텁입니다.");
    return new StubWageStatSeriesProvider();
  }
}
