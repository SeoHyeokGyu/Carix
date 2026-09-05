package com.carix.cpi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** KOSIS 지출목적별 소비자물가지수(DT_1J22001) OpenAPI 연동 클라이언트. */
@Slf4j
@Component
public class KosisCpiClient {

  private static final String KOSIS_PARAM_ENDPOINT =
      "https://kosis.kr/openapi/Param/statisticsParameterData.do";
  private static final String ORG_ID = "101";
  private static final String TBL_ID = "DT_1J22001";
  private static final String ITM_ID = "T"; // 소비자물가지수
  private static final String REGION_NATIONWIDE = "T10"; // objL1: 시도별 = 전국

  /** 지출목적별 12대 분류 코드 (A~L). 총지수 '0'은 cpi-category-map.yml 기준으로 제외한다. */
  private static final String[] CATEGORY_CODES = {
    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"
  };

  private final String apiKey;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @org.springframework.beans.factory.annotation.Autowired
  public KosisCpiClient(@Value("${kosis.api-key:${KOSIS_API_KEY:}}") String apiKey) {
    this(apiKey, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  KosisCpiClient(String apiKey, HttpClient httpClient) {
    this.apiKey = apiKey != null ? apiKey.trim() : "";
    this.httpClient = httpClient;
  }

  /**
   * 지정 연월 구간의 지출목적별 12대 분류 CPI를 조회합니다.
   *
   * @param startYearMonth 조회 시작 연월 (예: "202401", null/공백 시 최신 1개월)
   * @param endYearMonth 조회 종료 연월 (예: "202412", null/공백 시 startYearMonth와 동일)
   * @return 연월(yyyyMM) -&gt; (카테고리 코드 A~L -&gt; CPI 값) 맵
   */
  public Map<String, Map<String, BigDecimal>> fetchCpiByPeriod(
      String startYearMonth, String endYearMonth) {
    if (apiKey.isBlank()) {
      log.warn("KOSIS_API_KEY is not configured. Returning empty CPI data.");
      return Map.of();
    }

    String periodParam;
    if (startYearMonth != null && !startYearMonth.isBlank()) {
      String start = startYearMonth.trim();
      String end = (endYearMonth != null && !endYearMonth.isBlank()) ? endYearMonth.trim() : start;
      periodParam = "&prdSe=M&startPrdDe=" + start + "&endPrdDe=" + end;
    } else {
      periodParam = "&prdSe=M&newEstPrdCnt=1";
    }

    String objL2Query = String.join("+", CATEGORY_CODES);
    String url =
        KOSIS_PARAM_ENDPOINT
            + "?method=getList&apiKey="
            + apiKey
            + "&orgId="
            + ORG_ID
            + "&tblId="
            + TBL_ID
            + "&itmId="
            + ITM_ID
            + "&objL1="
            + REGION_NATIONWIDE
            + "&objL2="
            + objL2Query
            + periodParam
            + "&format=json&jsonVD=Y";

    try {
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(Duration.ofSeconds(15))
              .GET()
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("KOSIS CPI API HTTP error: status={}", response.statusCode());
        return Map.of();
      }

      JsonNode root = objectMapper.readTree(response.body());
      if (root.has("err")) {
        log.error(
            "KOSIS CPI API returned error: code={}, message={}",
            root.path("err").asText(),
            root.path("errMsg").asText());
        return Map.of();
      }

      if (!root.isArray()) {
        log.warn("KOSIS CPI API response is not a JSON array: {}", response.body());
        return Map.of();
      }

      Map<String, Map<String, BigDecimal>> result = new TreeMap<>();
      for (JsonNode row : root) {
        String prdDe = row.path("PRD_DE").asText("").trim();
        // 주의: objL1=지역, objL2=지출목적별 이므로 지출목적 코드는 C2 축에 담겨 온다.
        // (§2.3 축 순서 함정 — 실제 응답으로 반드시 재검증할 것)
        String c2 = row.path("C2").asText("");
        String dt = row.path("DT").asText("");

        if (!c2.isBlank() && !dt.isBlank()) {
          BigDecimal cpiValue = new BigDecimal(dt.trim().replace(",", ""));
          result.computeIfAbsent(prdDe, k -> new HashMap<>()).put(c2, cpiValue);
        }
      }

      log.info(
          "Successfully fetched KOSIS CPI data: {} months, {} total records",
          result.size(),
          root.size());
      return result;

    } catch (IOException | InterruptedException e) {
      log.error("Failed to query KOSIS CPI API", e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Map.of();
    }
  }
}
