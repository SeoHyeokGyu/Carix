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

  private List<String> parseSkillNames(String json) {
    List<String> skills = new ArrayList<>();
    try {
      JsonNode array = OBJECT_MAPPER.readTree(json);
      for (JsonNode node : array) {
        if (node.has("name")) {
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
