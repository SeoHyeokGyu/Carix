package com.carix.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"carix.index.purchasing-power-warning-threshold=0.80",
		"carix.index.market-value-gap-warning-threshold=0.70"
})
class IndexThresholdPropertiesTest {

	@Autowired
	private IndexThresholdProperties properties;

	@Test
	void 설정값을_바인딩한다() {
		assertThat(properties.purchasingPowerWarningThreshold()).isEqualTo(0.80);
		assertThat(properties.marketValueGapWarningThreshold()).isEqualTo(0.70);
	}
}
