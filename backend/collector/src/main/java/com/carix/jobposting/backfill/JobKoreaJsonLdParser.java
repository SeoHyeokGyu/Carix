package com.carix.jobposting.backfill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/**
 * 상세페이지의 {@code application/ld+json} 블록에서 JobPosting 을 찾는다. 게시일이 없으면 월을 배정할 수 없으므로 비운다.
 *
 * <p>목록 크롤러는 열린 공고만 보지만 상세페이지는 마감 뒤에도 ID 로 열리고 JSON-LD 가 남는다. 백필은 이 성질에 기댄다.
 */
public class JobKoreaJsonLdParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String JOB_POSTING_TYPE = "JobPosting";
  private static final int ISO_DATE_LENGTH = 10;
  private static final int REGION_TOKENS = 2;

  public Optional<JobKoreaJsonLd> parse(String html) {
    if (html == null || html.isBlank()) {
      return Optional.empty();
    }
    for (Element script : Jsoup.parse(html).select("script[type=application/ld+json]")) {
      Optional<JobKoreaJsonLd> parsed = findJobPosting(readTree(script.data())).map(this::toRecord);
      if (parsed.isPresent()) {
        return parsed.filter(ld -> ld.datePosted() != null);
      }
    }
    return Optional.empty();
  }

  private JsonNode readTree(String json) {
    try {
      return OBJECT_MAPPER.readTree(json);
    } catch (Exception e) {
      return null;
    }
  }

  private Optional<JsonNode> findJobPosting(JsonNode node) {
    if (node == null) {
      return Optional.empty();
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        Optional<JsonNode> found = findJobPosting(child);
        if (found.isPresent()) {
          return found;
        }
      }
      return Optional.empty();
    }
    if (JOB_POSTING_TYPE.equals(node.path("@type").asText())) {
      return Optional.of(node);
    }
    return findJobPosting(node.get("@graph"));
  }

  private JobKoreaJsonLd toRecord(JsonNode node) {
    return new JobKoreaJsonLd(
        text(node.path("title")),
        text(node.path("hiringOrganization").path("name")),
        date(node.path("datePosted")),
        date(node.path("validThrough")),
        text(node.path("employmentType")),
        text(node.path("experienceRequirements")),
        region(text(node.path("jobLocation").path("address").path("streetAddress"))));
  }

  private static String text(JsonNode node) {
    if (node.isArray()) {
      return node.isEmpty() ? null : text(node.get(0));
    }
    if (!node.isValueNode()) {
      return null;
    }
    String value = node.asText().strip();
    return value.isEmpty() ? null : value;
  }

  private static LocalDate date(JsonNode node) {
    String value = text(node);
    if (value == null || value.length() < ISO_DATE_LENGTH) {
      return null;
    }
    try {
      return LocalDate.parse(value.substring(0, ISO_DATE_LENGTH));
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /** 목록 크롤러의 지역 칸(`서울 강남구`)과 같은 깊이로 맞춘다. */
  private static String region(String streetAddress) {
    if (streetAddress == null) {
      return null;
    }
    String[] tokens = streetAddress.split("\\s+");
    return String.join(
        " ", java.util.Arrays.copyOf(tokens, Math.min(REGION_TOKENS, tokens.length)));
  }
}
