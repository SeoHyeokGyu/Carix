package com.carix.jobposting.wanted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WantedParserTest {

  private final WantedParser parser = new WantedParser();

  private static String fixture(String name) throws IOException {
    try (var in = WantedParserTest.class.getResourceAsStream("/wanted/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("목록 API 에서 공고 번호·회사·제목·하위 직군을 읽는다")
  void parsesList() throws IOException {
    WantedParser.ListPage page = parser.parseList(fixture("list.json"));
    assertThat(page.items()).hasSize(3);
    assertThat(page.last()).isFalse();
    WantedListItem first = page.items().get(0);
    assertThat(first.id()).isEqualTo("391003");
    assertThat(first.company()).isEqualTo("콕스웨이브");
    assertThat(first.title()).isEqualTo("[AX krewa] 리서치 엔지니어 (Lead)");
    assertThat(first.childTagIds()).containsExactly(1634);
  }

  @Test
  @DisplayName("다음 페이지 링크가 null 이거나 data 가 비면 마지막 페이지다")
  void detectsLastPage() {
    assertThat(parser.parseList("{\"links\":{\"next\":null},\"data\":[{\"id\":1}]}").last())
        .isTrue();
    assertThat(parser.parseList("{\"links\":{\"next\":\"/api/x\"},\"data\":[]}").last()).isTrue();
  }

  @Test
  @DisplayName("data 가 없으면 구조 변경으로 본다")
  void brokenListThrows() {
    assertThatThrownBy(() -> parser.parseList("{\"links\":{}}"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("진행 중 공고 페이지에서 게시일·직군·경력·지역·고용형태·본문을 읽는다")
  void parsesActivePage() throws IOException {
    WantedPosting p = parser.parsePage(fixture("page-active.html")).orElseThrow();
    assertThat(p.id()).isEqualTo("391003");
    assertThat(p.company()).isEqualTo("콕스웨이브");
    assertThat(p.postedDate()).isEqualTo(LocalDate.of(2026, 10, 7));
    assertThat(p.endDate()).isNull();
    assertThat(p.parentTagId()).isEqualTo(518);
    assertThat(p.childTagIds()).containsExactly(1634);
    assertThat(p.childTagNames()).containsExactly("머신러닝 엔지니어");
    assertThat(p.annualFrom()).isEqualTo(3);
    assertThat(p.annualTo()).isEqualTo(35);
    assertThat(p.newbie()).isFalse();
    assertThat(p.region()).isEqualTo("서울 영등포구");
    assertThat(p.employmentType()).isEqualTo("FULL_TIME");
    assertThat(p.bodyText()).startsWith("[AX Team, krewa]").contains("Trust System");
  }

  @Test
  @DisplayName("annual_to 100 은 상한 없음이라 null 로 저장한다")
  void annualToSentinelBecomesNull() throws IOException {
    String html = fixture("page-active.html").replace("\"annual_to\":35", "\"annual_to\":100");
    WantedPosting p = parser.parsePage(html).orElseThrow();
    assertThat(p.annualFrom()).isEqualTo(3);
    assertThat(p.annualTo()).isNull();
  }

  @Test
  @DisplayName("annual_to 100 미만은 그대로 저장한다")
  void annualToBelowSentinelKept() throws IOException {
    String html = fixture("page-active.html").replace("\"annual_to\":35", "\"annual_to\":10");
    assertThat(parser.parsePage(html).orElseThrow().annualTo()).isEqualTo(10);
  }

  @Test
  @DisplayName("마감 공고 페이지도 게시일을 읽고 마감일은 close_time 으로 대체한다")
  void parsesClosedPage() throws IOException {
    WantedPosting p = parser.parsePage(fixture("page-closed.html")).orElseThrow();
    assertThat(p.id()).isEqualTo("315000");
    assertThat(p.company()).isEqualTo("리샵");
    assertThat(p.postedDate()).isEqualTo(LocalDate.of(2025, 11, 17));
    assertThat(p.endDate()).isEqualTo(LocalDate.of(2025, 11, 19));
    assertThat(p.parentTagId()).isEqualTo(523);
    assertThat(p.childTagIds()).containsExactly(707, 710, 719);
  }

  @Test
  @DisplayName("__NEXT_DATA__ 가 없는 200 페이지는 빈 값이다")
  void noDataPageIsEmpty() throws IOException {
    assertThat(parser.parsePage(fixture("page-nodata.html"))).isEmpty();
  }

  @Test
  @DisplayName("상세 API 에서 스킬 태그 이름을 읽고 깨진 응답은 빈 목록이다")
  void parsesSkillTags() throws IOException {
    assertThat(parser.parseSkillTags(fixture("detail-api.json")))
        .isEqualTo(List.of("Kotlin", "Node.js", "AWS", "Golang"));
    assertThat(parser.parseSkillTags("<html>oops</html>")).isEmpty();
  }

  private static String nextData(String initialData) {
    return "<html><script id=\"__NEXT_DATA__\" type=\"application/json\">"
        + "{\"props\":{\"pageProps\":{"
        + initialData
        + "}}}</script></html>";
  }

  @Test
  @DisplayName("initialData 가 없으면 빈 값이다")
  void missingInitialDataIsEmpty() {
    assertThat(parser.parsePage(nextData("\"other\":{}"))).isEmpty();
  }
}
