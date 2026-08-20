package com.carix.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/**
 * 잡코리아 상세페이지 static HTML에 포함된 Next.js RSC 스트리밍 페이로드
 * (self.__next_f.push)에서 스킬·급여 JSON 블록을 문자열 마커 + 괄호매칭으로
 * 추출한다. 헤드리스 브라우저 없이 정적 HTML만으로 동작한다.
 */
public class JobKoreaDetailParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String SKILLS_MARKER = "\\\"skills\\\":[";
  private static final String PAY_MARKER = "\\\"pay\\\":{";
  private static final String JOB_TYPE_NAME_MARKER = "\\\"jobTypeName\\\":\\\"";
  private static final String VALUE_END_MARKER = "\\\"";
  private static final String HARD_SKILL_TYPE = "HARD_SKILL";
  private static final String EMPLOYEE_COUNT_MARKER = "\\\"employeeCount\\\":";
  private static final String COMPANY_TYPE_MARKER = "\\\"companyTypeName\\\":\\\"";

  public List<String> extractSkills(String detailHtml) {
    int searchFrom = 0;
    while (true) {
      int markerIdx = detailHtml.indexOf(SKILLS_MARKER, searchFrom);
      if (markerIdx == -1) {
        return List.of();
      }
      int bracketStart = markerIdx + SKILLS_MARKER.length() - 1;
      String rawJson = extractBalanced(detailHtml, bracketStart, '[', ']');
      searchFrom = markerIdx + SKILLS_MARKER.length();
      if (rawJson == null) {
        continue;
      }
      List<String> skills = parseSkillNames(unescape(rawJson));
      if (!skills.isEmpty()) {
        return skills;
      }
    }
  }

  public String extractSalaryRaw(String detailHtml) {
    int markerIdx = detailHtml.indexOf(PAY_MARKER);
    if (markerIdx == -1) {
      return "";
    }
    int braceStart = markerIdx + PAY_MARKER.length() - 1;
    String rawJson = extractBalanced(detailHtml, braceStart, '{', '}');
    return rawJson == null ? "" : unescape(rawJson);
  }

  /**
   * 회사 사원수를 준다. 원천이 값을 모르면 {@code null} 을 담아 주므로 그대로 null 을 돌려준다.
   * 회사 규모 판정의 1차 근거다 (실측 400건 중 386건에 값이 있다).
   */
  public Integer extractEmployeeCount(String detailHtml) {
    int markerIdx = detailHtml.indexOf(EMPLOYEE_COUNT_MARKER);
    if (markerIdx == -1) {
      return null;
    }
    int valueStart = markerIdx + EMPLOYEE_COUNT_MARKER.length();
    int valueEnd = valueStart;
    while (valueEnd < detailHtml.length() && Character.isDigit(detailHtml.charAt(valueEnd))) {
      valueEnd++;
    }
    if (valueEnd == valueStart) {
      return null;
    }
    try {
      return Integer.valueOf(detailHtml.substring(valueStart, valueEnd));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** 기업 구분 표기(`중소기업`·`벤처기업` 등). 사원수가 없을 때의 보조 근거다. */
  public String extractCompanyTypeName(String detailHtml) {
    return extractQuotedValue(detailHtml, COMPANY_TYPE_MARKER);
  }

  /**
   * 공고에 붙은 직무명을 준다. 직무가 여러 개면 원천이 콤마로 이어 주고, 개수가 많으면
   * `외 12건` 꼬리표를 붙인다 — 판정은 호출부에 맡기고 여기서는 원문 그대로 반환한다.
   */
  public String extractJobTypeName(String detailHtml) {
    return extractQuotedValue(detailHtml, JOB_TYPE_NAME_MARKER);
  }

  /** 이스케이프된 페이로드에서 {@code marker} 바로 뒤의 문자열 값을 꺼낸다. */
  private String extractQuotedValue(String detailHtml, String marker) {
    int markerIdx = detailHtml.indexOf(marker);
    if (markerIdx == -1) {
      return null;
    }
    int valueStart = markerIdx + marker.length();
    int valueEnd = detailHtml.indexOf(VALUE_END_MARKER, valueStart);
    if (valueEnd == -1) {
      return null;
    }
    String value = unescape(detailHtml.substring(valueStart, valueEnd)).strip();
    return value.isEmpty() ? null : value;
  }

  /**
   * 원천은 직무 역량(HARD_SKILL)과 인성 역량(SOFT_SKILL)을 한 배열에 담아 준다.
   * 인성 역량이 섞이면 스킬 수요 상위가 `성실성`·`꼼꼼함`으로 채워지므로 직무 역량만 남긴다.
   */
  private List<String> parseSkillNames(String json) {
    List<String> skills = new ArrayList<>();
    try {
      JsonNode array = OBJECT_MAPPER.readTree(json);
      for (JsonNode node : array) {
        if (node.has("name") && HARD_SKILL_TYPE.equals(node.path("skillTypeCode").asText())) {
          skills.add(node.path("name").asText());
        }
      }
    } catch (Exception e) {
      System.err.println("[JobKoreaDetailParser] Failed to parse skills JSON: " + e.getMessage());
    }
    return skills;
  }

  private String extractBalanced(String html, int openIndex, char open, char close) {
    int depth = 0;
    for (int i = openIndex; i < html.length(); i++) {
      char c = html.charAt(i);
      if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) {
          return html.substring(openIndex, i + 1);
        }
      }
    }
    return null;
  }

  private String unescape(String s) {
    return s
        .replace("\\\\\\\"", "\"")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\");
  }
}
