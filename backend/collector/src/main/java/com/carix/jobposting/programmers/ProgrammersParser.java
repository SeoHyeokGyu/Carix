package com.carix.jobposting.programmers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** 프로그래머스 채용공고 목록·상세 API 응답을 파싱한다. */
public class ProgrammersParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int DATE_LENGTH = 10;
  private static final int PLACEHOLDER_YEAR = 9000;

  public record Page(
      List<ProgrammersPosition> positions,
      int totalCount,
      int totalPages,
      int skipped,
      boolean empty) {}

  public Page parsePage(String json) {
    JsonNode root = read(json);
    JsonNode positions = root.path("job_positions");
    if (!positions.isArray()) {
      positions = root.path("jobPositions");
    }
    if (!positions.isArray()) {
      throw new IllegalStateException("프로그래머스 목록 응답에 job_positions 가 없습니다");
    }

    int totalCount = root.path("total_count").asInt(root.path("totalCount").asInt(0));
    int totalPages = root.path("total_pages").asInt(root.path("totalPages").asInt(0));

    List<ProgrammersPosition> out = new ArrayList<>();
    int skipped = 0;

    for (JsonNode p : positions) {
      String id = p.path("id").asText("");
      if (id.isBlank()) {
        skipped++;
        continue;
      }

      String companyName = p.path("company").path("name").asText("");
      if (companyName.isBlank()) {
        companyName = p.path("companyName").asText("");
      }

      out.add(
          new ProgrammersPosition(
              id,
              p.path("title").asText(""),
              companyName,
              p.path("address").asText(""),
              intOrNull(p.has("min_career") ? p.path("min_career") : p.path("minCareer")),
              intOrNull(p.has("max_career") ? p.path("max_career") : p.path("maxCareer")),
              extractStrings(
                  p.has("position_tags") ? p.path("position_tags") : p.path("positionTags")),
              extractStrings(
                  p.has("job_categories") ? p.path("job_categories") : p.path("jobCategories")),
              date(p.has("start_at") ? p.path("start_at") : p.path("startAt")),
              closedAt(p),
              p.path("active").asBoolean(true)));
    }

    return new Page(out, totalCount, totalPages, skipped, positions.isEmpty());
  }

  private static JsonNode read(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("프로그래머스 응답을 JSON 으로 읽지 못했습니다", e);
    }
  }

  private static List<String> extractStrings(JsonNode array) {
    if (!array.isArray()) {
      return List.of();
    }
    List<String> list = new ArrayList<>();
    for (JsonNode n : array) {
      String text = n.isObject() ? n.path("name").asText("") : n.asText("");
      String stripped = text.strip();
      if (!stripped.isEmpty()) {
        list.add(stripped);
      }
    }
    return list;
  }

  private static Integer intOrNull(JsonNode n) {
    return (n != null && n.isNumber()) ? n.asInt() : null;
  }

  private static LocalDate closedAt(JsonNode position) {
    JsonNode endNode = position.has("end_at") ? position.path("end_at") : position.path("endAt");
    LocalDate closed = date(endNode);
    return closed != null && closed.getYear() >= PLACEHOLDER_YEAR ? null : closed;
  }

  private static LocalDate date(JsonNode n) {
    if (n == null || !n.isTextual()) {
      return null;
    }
    String v = n.asText().strip();
    if (v.length() < DATE_LENGTH) {
      return null;
    }
    try {
      return LocalDate.parse(v.substring(0, DATE_LENGTH));
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
