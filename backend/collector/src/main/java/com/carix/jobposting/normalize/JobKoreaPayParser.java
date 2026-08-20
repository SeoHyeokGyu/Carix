package com.carix.jobposting.normalize;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 잡코리아 상세페이지 RSC 페이로드의 {@code pay} 블록에서 연봉 범위를 뽑는다.
 *
 * <p>목록페이지 셀 텍스트가 아니라 이 블록을 쓰는 이유는 커버리지다 — 실측 400건 기준
 * 목록페이지 파싱은 36건만 성공했고 이 블록은 317건에서 공개 연봉을 담고 있었다.
 */
public class JobKoreaPayParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String ANNUAL_PAY_TYPE = "ANNUALLY_SALARY";

  /** 공개 연봉이 없거나 연봉 외 급여 형태(월급·시급)면 null 을 준다. */
  public SalaryRange parse(String payJson) {
    if (payJson == null || payJson.isBlank()) {
      return null;
    }
    JsonNode pay;
    try {
      pay = OBJECT_MAPPER.readTree(payJson);
    } catch (Exception e) {
      return null;
    }
    if (!ANNUAL_PAY_TYPE.equals(pay.path("payType").asText())) {
      return null;
    }
    JsonNode range = pay.path("payRange");
    if (!range.hasNonNull("from")) {
      return null;
    }
    long min = range.path("from").asLong();
    if (!range.hasNonNull("to")) {
      return new SalaryRange(min, min);
    }
    long to = range.path("to").asLong();
    // 상한이 배타적이면 표기값 그대로 쓰면 한 구간 위로 부풀려진다 (3801 -> 3800).
    long max = range.path("toInclusive").asBoolean(false) ? to : to - 1;
    return new SalaryRange(min, Math.max(min, max));
  }
}
