package com.carix.jobposting.jumpit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 점핏 목록·상세 API 응답을 읽는다. */
public class JumpitParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 날짜는 `2026-11-05T23:59:59`(목록) 또는 `2026-10-07 00:00:00`(상세) — 앞 10자가 날짜다. */
  private static final int DATE_LENGTH = 10;

  private static final int PLACEHOLDER_YEAR = 9000;

  public record Page(List<JumpitPosition> positions, int skipped, boolean empty) {}

  public Page parsePage(String json) {
    JsonNode positions = read(json).path("result").path("positions");
    if (!positions.isArray()) {
      throw new IllegalStateException("점핏 목록 응답에 result.positions 가 없습니다");
    }
    List<JumpitPosition> out = new ArrayList<>();
    int skipped = 0;
    for (JsonNode p : positions) {
      String id = p.path("id").asText("");
      if (id.isBlank()) {
        skipped++;
        continue;
      }
      out.add(
          new JumpitPosition(
              id,
              p.path("title").asText(""),
              p.path("companyName").asText(""),
              splitCategories(p.path("jobCategory").asText("")),
              strings(p.path("techStacks")),
              intOrNull(p.path("minCareer")),
              intOrNull(p.path("maxCareer")),
              p.path("newcomer").asBoolean(false),
              strings(p.path("locations")).stream().findFirst().orElse(null),
              closedAt(p)));
    }
    return new Page(out, skipped, positions.isEmpty());
  }

  public Optional<LocalDate> parsePublishedAt(String json) {
    return Optional.ofNullable(date(read(json).path("result").path("publishedAt")));
  }

  private static JsonNode read(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("점핏 응답을 JSON 으로 읽지 못했습니다", e);
    }
  }

  private static List<String> splitCategories(String raw) {
    return Arrays.stream(raw.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
  }

  private static List<String> strings(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(
        n -> {
          String v = n.asText("").strip();
          if (!v.isEmpty()) {
            out.add(v);
          }
        });
    return out;
  }

  private static Integer intOrNull(JsonNode n) {
    return n.isNumber() ? n.asInt() : null;
  }

  /** 상시 채용은 마감일이 없고, 9999-12-31 같은 먼 미래 값도 마감일이 아니다. */
  private static LocalDate closedAt(JsonNode position) {
    if (position.path("alwaysOpen").asBoolean(false)) {
      return null;
    }
    LocalDate closed = date(position.path("closedAt"));
    return closed != null && closed.getYear() >= PLACEHOLDER_YEAR ? null : closed;
  }

  private static LocalDate date(JsonNode n) {
    String v = n.isTextual() ? n.asText().strip() : "";
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
