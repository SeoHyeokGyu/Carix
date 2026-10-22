package com.carix.wagestat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KosisWageStatClientTest {

  @Test
  @DisplayName("fetchWageDistributionByPeriod - API 키가 비어있으면 빈 맵을 안전하게 반환한다")
  void fetchWageDistributionByPeriod_returnsEmptyWhenNoApiKey() {
    KosisWageStatClient client = new KosisWageStatClient("");
    Map<String, Map<String, Long>> result =
        client.fetchWageDistributionByPeriod("05", "2020", "2024");
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("fetchWageDistributionByPeriod - 200 OK 응답 정상 파싱")
  @SuppressWarnings("unchecked")
  void fetchWageDistributionByPeriod_success() throws Exception {
    HttpClient httpClient = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);

    String json =
        """
        [
          {
            "PRD_DE": "2024",
            "C3": "13102205073PAYGRADE.A01",
            "DT": "1,500"
          },
          {
            "PRD_DE": "2024",
            "C3": "13102205073PAYGRADE.A02",
            "DT": "2,000"
          }
        ]
        """;

    given(response.statusCode()).willReturn(200);
    given(response.body()).willReturn(json);
    org.mockito.Mockito.doReturn(response).when(httpClient).send(any(), any());

    KosisWageStatClient client = new KosisWageStatClient("test-key", httpClient);
    Map<String, Map<String, Long>> result =
        client.fetchWageDistributionByPeriod("05", "2024", "2024");

    assertThat(result).containsKey("2024");
    assertThat(result.get("2024")).containsEntry("A01", 1500L);
    assertThat(result.get("2024")).containsEntry("A02", 2000L);

    // fetchWageDistributionForAge 도 최신 연도 데이터를 정상 반환해야 함
    Map<String, Long> forAge = client.fetchWageDistributionForAge("05");
    assertThat(forAge).containsEntry("A01", 1500L);
  }

  @Test
  @DisplayName("fetchWageDistributionByPeriod - KOSIS 에러 응답 처리 (err 필드)")
  @SuppressWarnings("unchecked")
  void fetchWageDistributionByPeriod_errResponse() throws Exception {
    HttpClient httpClient = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);

    given(response.statusCode()).willReturn(200);
    given(response.body())
        .willReturn(
            """
        {"err":"99","errMsg":"Invalid key"}
        """);
    org.mockito.Mockito.doReturn(response).when(httpClient).send(any(), any());

    KosisWageStatClient client = new KosisWageStatClient("test-key", httpClient);
    Map<String, Map<String, Long>> result = client.fetchWageDistributionByPeriod("05", null, null);

    assertThat(result).isEmpty();
  }
}
