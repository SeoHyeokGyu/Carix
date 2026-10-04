package com.carix.jobposting.backfill;

import com.carix.jobposting.normalize.ExperienceRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 상세페이지 RSC 페이로드에서 그 공고 자신의 경력 구간과 직무 분류 코드를 읽는다.
 *
 * <p>{@code jobTypeName} 은 페이지 아래 추천 공고 목록의 값이라 마감 공고에는 아예 없다. 마감 뒤에도 남는 자기 공고 블록({@code careers},
 * {@code category.jobClassificationCodes})만 쓴다.
 */
public class JobKoreaOwnPostingParser {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern CAREERS = Pattern.compile("careers\\\\\":(\\[.*?\\])");
  private static final Pattern CLASSIFICATION_CODES =
      Pattern.compile("jobClassificationCodes\\\\\":\\[([^\\]]*)\\]");
  private static final Pattern CODE = Pattern.compile("\\d+");

  private static final ExperienceRange UNKNOWN = new ExperienceRange(null, null);
  private static final ExperienceRange NO_FLOOR = new ExperienceRange(0, null);
  private static final ExperienceRange NEWBIE_ONLY = new ExperienceRange(0, 0);

  public ExperienceRange extractExperience(String html) {
    Matcher matcher = CAREERS.matcher(html);
    if (!matcher.find()) {
      return UNKNOWN;
    }
    JsonNode careers;
    try {
      careers = OBJECT_MAPPER.readTree(matcher.group(1).replace("\\\"", "\""));
    } catch (Exception e) {
      return UNKNOWN;
    }
    boolean newbie = false;
    JsonNode experienced = null;
    for (JsonNode career : careers) {
      switch (career.path("type").asText()) {
        case "ANY" -> {
          return NO_FLOOR;
        }
        case "NEWBIE" -> newbie = true;
        case "EXPERIENCED" -> experienced = career.path("range");
        default -> {}
      }
    }
    // 신입을 받으면 경력 연차는 우대 조건이다 — ExperienceRange.parse 의 `신입·경력` 규약과 같다.
    if (newbie) {
      return experienced != null ? NO_FLOOR : NEWBIE_ONLY;
    }
    if (experienced == null || !experienced.path("from").isNumber()) {
      return UNKNOWN;
    }
    JsonNode to = experienced.path("to");
    return new ExperienceRange(experienced.path("from").asInt(), to.isNumber() ? to.asInt() : null);
  }

  public List<String> extractClassificationCodes(String html) {
    Matcher matcher = CLASSIFICATION_CODES.matcher(html);
    if (!matcher.find()) {
      return List.of();
    }
    List<String> codes = new ArrayList<>();
    Matcher code = CODE.matcher(matcher.group(1));
    while (code.find()) {
      codes.add(code.group());
    }
    return codes;
  }
}
