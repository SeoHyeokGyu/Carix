package com.carix.cpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KosisCpiClientTest {

  private static final String JSON =
      """
      [
        {"PRD_DE": "202606", "C2": "0", "DT": "116.52"},
        {"PRD_DE": "202606", "C2": "A", "DT": "120.10"}
      ]
      """;

  @SuppressWarnings("unchecked")
  private HttpClient stubbed(String body) throws Exception {
    HttpClient httpClient = mock(HttpClient.class);
    HttpResponse<String> response = mock(HttpResponse.class);
    given(response.statusCode()).willReturn(200);
    given(response.body()).willReturn(body);
    org.mockito.Mockito.doReturn(response).when(httpClient).send(any(), any());
    return httpClient;
  }

  @Test
  @DisplayName("요청 objL2 에 총지수 0 과 12대 분류가 함께 들어간다")
  void 요청에_총지수가_포함된다() throws Exception {
    HttpClient httpClient = stubbed("[]");

    new KosisCpiClient("key", httpClient).fetchCpiByPeriod("202606", "202606");

    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    assertThat(captor.getValue().uri().toString()).contains("&objL2=0+A+B+C+D+E+F+G+H+I+J+K+L&");
  }

  @Test
  @DisplayName("응답 C2=0 행이 결과 맵에 키 0 으로 담긴다")
  void 총지수_행이_0_키로_담긴다() throws Exception {
    HttpClient httpClient = stubbed(JSON);

    Map<String, Map<String, BigDecimal>> result =
        new KosisCpiClient("key", httpClient).fetchCpiByPeriod("202606", "202606");

    assertThat(result.get("202606")).containsEntry("0", new BigDecimal("116.52"));
    assertThat(result.get("202606")).containsKey("A");
  }
}
