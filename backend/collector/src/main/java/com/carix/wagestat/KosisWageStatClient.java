package com.carix.wagestat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** KOSIS 임금 통계(고용형태별근로실태조사, DT_118N_PAYM39) OpenAPI 연동 클라이언트. */
@Slf4j
@Component
public class KosisWageStatClient {

  private static final String KOSIS_PARAM_ENDPOINT =
      "https://kosis.kr/openapi/Param/statisticsParameterData.do";
  private static final String ORG_ID = "118";
  private static final String TBL_ID = "DT_118N_PAYM39";
  private static final String ITM_ID = "13103205073WORKERCNT";
  private static final String SEX_TOTAL = "13102205073SEXS.0";
  private static final String OCCUPATION_EXPERT = "210517KCSO6_2"; // 전문가 및 관련종사자(2)

  private static final String PAYGRADE_ALL_QUERY =
      "13102205073PAYGRADE.A01+13102205073PAYGRADE.A02+13102205073PAYGRADE.A03+13102205073PAYGRADE.A04+"
          + "13102205073PAYGRADE.A05+13102205073PAYGRADE.A06+13102205073PAYGRADE.A07+13102205073PAYGRADE.A08+"
          + "13102205073PAYGRADE.A09+13102205073PAYGRADE.A10+13102205073PAYGRADE.A11+13102205073PAYGRADE.A12+"
          + "13102205073PAYGRADE.A13+13102205073PAYGRADE.A14+13102205073PAYGRADE.A15+13102205073PAYGRADE.A16+"
          + "13102205073PAYGRADE.A17+13102205073PAYGRADE.A18+13102205073PAYGRADE.A19+13102205073PAYGRADE.A20+"
          + "13102205073PAYGRADE.A21+13102205073PAYGRADE.A22+13102205073PAYGRADE.A23+13102205073PAYGRADE.A24";

  private final String apiKey;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public KosisWageStatClient(@Value("${kosis.api-key:${KOSIS_API_KEY:}}") String apiKey) {
    this.apiKey = apiKey != null ? apiKey.trim() : "";
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  /**
   * 지정한 연령대 코드의 전문가 직종 24개 임금계층별 근로자 수 분포를 조회합니다.
   *
   * @param ageCode 연령대 접미 코드 (예: "00"=전체, "04"=25~29세, "05"=30~34세)
   * @return 임금계층 코드(A01~A24)별 근로자 수 맵
   */
  public Map<String, Long> fetchWageDistributionForAge(String ageCode) {
    if (apiKey.isBlank()) {
      log.warn("KOSIS_API_KEY is not configured. Returning empty wage distribution.");
      return Map.of();
    }

    String ageParam = "13102205073AGEGRADE." + ageCode;
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
            + OCCUPATION_EXPERT
            + "&objL2="
            + SEX_TOTAL
            + "&objL3="
            + PAYGRADE_ALL_QUERY
            + "&objL4="
            + ageParam
            + "&prdSe=Y&newEstPrdCnt=1"
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
        log.error("KOSIS API HTTP error: status={}", response.statusCode());
        return Map.of();
      }

      JsonNode root = objectMapper.readTree(response.body());
      if (root.has("err")) {
        log.error(
            "KOSIS API returned error: code={}, message={}",
            root.path("err").asText(),
            root.path("errMsg").asText());
        return Map.of();
      }

      if (!root.isArray()) {
        log.warn("KOSIS API response is not a JSON array: {}", response.body());
        return Map.of();
      }

      Map<String, Long> counts = new HashMap<>();
      for (JsonNode row : root) {
        String c3 = row.path("C3").asText("");
        String dt = row.path("DT").asText("0");

        if (c3.contains("PAYGRADE.")) {
          String gradeCode = c3.substring(c3.indexOf("PAYGRADE.") + 9);
          if (gradeCode.startsWith("A")) {
            long workerCount = Long.parseLong(dt.trim().replace(",", ""));
            counts.put(gradeCode, workerCount);
          }
        }
      }

      log.info(
          "Successfully fetched KOSIS wage distribution for ageCode={} ({} bracket records)",
          ageCode,
          counts.size());
      return counts;

    } catch (IOException | InterruptedException e) {
      log.error("Failed to query KOSIS wage stat API for ageCode={}", ageCode, e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return Map.of();
    }
  }
}
