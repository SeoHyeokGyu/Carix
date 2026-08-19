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
   * 공고에 붙은 직무명을 준다. 직무가 여러 개면 원천이 콤마로 이어 주고, 개수가 많으면
   * `외 12건` 꼬리표를 붙인다 — 판정은 호출부에 맡기고 여기서는 원문 그대로 반환한다.
   */
  public String extractJobTypeName(String detailHtml) {
    int markerIdx = detailHtml.indexOf(JOB_TYPE_NAME_MARKER);
    if (markerIdx == -1) {
      return null;
    }
    int valueStart = markerIdx + JOB_TYPE_NAME_MARKER.length();
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
