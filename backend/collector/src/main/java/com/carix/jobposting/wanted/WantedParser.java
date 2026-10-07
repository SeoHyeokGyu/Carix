package com.carix.jobposting.wanted;

import com.carix.jobposting.normalize.EmploymentTypeClassifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 원티드 목록 API·공고 페이지 {@code __NEXT_DATA__}·상세 API 를 읽는다. */
public class WantedParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern NEXT_DATA =
      Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>", Pattern.DOTALL);
  private static final int DATE_LENGTH = 10;
  private static final Map<String, String> EMPLOYMENT =
      Map.of(
          "regular", EmploymentTypeClassifier.FULL_TIME,
          "contract", EmploymentTypeClassifier.CONTRACT,
          "intern", EmploymentTypeClassifier.INTERN,
          "freelance", EmploymentTypeClassifier.FREELANCE);
  private static final List<String> BODY_FIELDS =
      List.of("intro", "main_tasks", "requirements", "preferred_points");

  public record ListPage(List<WantedListItem> items, boolean last) {}

  public ListPage parseList(String json) {
    JsonNode root = read(json);
    JsonNode data = root.path("data");
    if (!data.isArray()) {
      throw new IllegalStateException("원티드 목록 응답에 data 가 없습니다");
    }
    List<WantedListItem> items = new ArrayList<>();
    for (JsonNode j : data) {
      String id = j.path("id").asText("");
      if (id.isBlank()) {
        continue;
      }
      List<Integer> child = new ArrayList<>();
      j.path("category_tags").forEach(t -> child.add(t.path("id").asInt()));
      items.add(
          new WantedListItem(
              id, j.path("company").path("name").asText(""), j.path("position").asText(""), child));
    }
    JsonNode next = root.path("links").path("next");
    return new ListPage(items, items.isEmpty() || next.isNull() || next.isMissingNode());
  }

  public Optional<WantedPosting> parsePage(String html) {
    Matcher m = NEXT_DATA.matcher(html == null ? "" : html);
    if (!m.find()) {
      return Optional.empty();
    }
    JsonNode d;
    try {
      d = MAPPER.readTree(m.group(1)).path("props").path("pageProps").path("initialData");
    } catch (Exception e) {
      return Optional.empty();
    }
    LocalDate posted = date(d.path("confirm_time"));
    JsonNode category = d.path("category_tag");
    if (d.isMissingNode() || posted == null || !category.isObject()) {
      return Optional.empty();
    }
    List<Integer> childIds = new ArrayList<>();
    List<String> childNames = new ArrayList<>();
    category
        .path("child_tags")
        .forEach(
            t -> {
              childIds.add(t.path("id").asInt());
              childNames.add(t.path("text").asText(""));
            });
    JsonNode career = d.path("career");
    JsonNode address = d.path("address");
    String region =
        (address.path("location").asText("") + " " + address.path("district").asText("")).strip();
    LocalDate end =
        Optional.ofNullable(date(d.path("due_time"))).orElse(date(d.path("close_time")));
    StringBuilder body = new StringBuilder();
    BODY_FIELDS.forEach(f -> body.append(d.path(f).asText("")).append('\n'));
    return Optional.of(
        new WantedPosting(
            d.path("id").asText(""),
            d.path("company").path("company_name").asText(""),
            d.path("position").asText(""),
            posted,
            end,
            category.path("parent_tag").path("id").asInt(),
            childIds,
            childNames,
            intOrNull(career.path("annual_from")),
            intOrNull(career.path("annual_to")),
            career.path("is_newbie").asBoolean(false),
            region.isBlank() ? null : region,
            EMPLOYMENT.getOrDefault(
                d.path("employment_type").asText(""), EmploymentTypeClassifier.UNKNOWN),
            body.toString().strip()));
  }

  public List<String> parseSkillTags(String json) {
    List<String> out = new ArrayList<>();
    try {
      MAPPER
          .readTree(json)
          .path("job")
          .path("skill_tags")
          .forEach(
              t -> {
                String title = t.path("title").asText("").strip();
                if (!title.isEmpty()) {
                  out.add(title);
                }
              });
    } catch (Exception e) {
      return List.of();
    }
    return out;
  }

  private static JsonNode read(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("원티드 응답을 JSON 으로 읽지 못했습니다", e);
    }
  }

  private static Integer intOrNull(JsonNode n) {
    return n.isNumber() ? n.asInt() : null;
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
