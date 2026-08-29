package com.carix.wagestat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KosisWageStatClientTest {

  @Test
  @DisplayName("fetchWageDistributionForAge - API 키가 비어있으면 빈 맵을 안전하게 반환한다")
  void fetchWageDistributionForAge_returnsEmptyWhenNoApiKey() {
    KosisWageStatClient client = new KosisWageStatClient("");
    Map<String, Long> result = client.fetchWageDistributionForAge("05");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("fetchWageDistributionByPeriod - API 키가 비어있으면 빈 맵을 안전하게 반환한다")
  void fetchWageDistributionByPeriod_returnsEmptyWhenNoApiKey() {
    KosisWageStatClient client = new KosisWageStatClient("");
    Map<String, Map<String, Long>> result =
        client.fetchWageDistributionByPeriod("05", "2020", "2024");
    assertThat(result).isEmpty();
  }
}
