package com.carix.jobposting.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.normalize.ExperienceRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobKoreaOwnPostingParserTest {

  private final JobKoreaOwnPostingParser parser = new JobKoreaOwnPostingParser();

  /** 상세페이지 RSC 페이로드는 따옴표가 역슬래시로 이스케이프된 채 들어 있다. */
  private static String rsc(String json) {
    return "<script>self.__next_f.push([1,\"" + json.replace("\"", "\\\"") + "\"])</script>";
  }

  @Test
  @DisplayName("경력 구간을 careers 에서 읽는다 — JSON-LD 는 「경력」 한 단어뿐이라 연차를 모른다")
  void experiencedRange() {
    String html =
        rsc(
            "\"careers\":[{\"type\":\"EXPERIENCED\",\"range\":{\"from\":5,\"to\":10,"
                + "\"fromInclusive\":true,\"toInclusive\":true}}],\"x\":1");

    assertThat(parser.extractExperience(html)).isEqualTo(new ExperienceRange(5, 10));
  }

  @Test
  @DisplayName("연차 없는 경력이나 careers 가 없으면 모름이다")
  void unknownCareer() {
    assertThat(
            parser.extractExperience(
                rsc("\"careers\":[{\"type\":\"EXPERIENCED\",\"range\":null}],")))
        .isEqualTo(new ExperienceRange(null, null));
    assertThat(parser.extractExperience("<html></html>"))
        .isEqualTo(new ExperienceRange(null, null));
  }

  @Test
  @DisplayName("자기 공고의 직무 분류 코드를 읽는다")
  void classificationCodes() {
    String html =
        rsc(
            "\"category\":{\"jobClassificationCodes\":[\"4010000171\",\"4010000176\"],"
                + "\"industryCodes\":[\"3010000046\"]}");

    assertThat(parser.extractClassificationCodes(html)).containsExactly("4010000171", "4010000176");
    assertThat(parser.extractClassificationCodes("<html></html>")).isEmpty();
  }
}
