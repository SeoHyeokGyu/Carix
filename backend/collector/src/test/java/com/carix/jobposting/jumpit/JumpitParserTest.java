package com.carix.jobposting.jumpit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JumpitParserTest {

  private final JumpitParser parser = new JumpitParser();

  private static String fixture(String name) throws IOException {
    try (var in = JumpitParserTest.class.getResourceAsStream("/jumpit/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("목록 한 페이지에서 공고 필드를 읽고 번호 없는 공고는 skipped 로 센다")
  void parsesPage() throws IOException {
    JumpitParser.Page page = parser.parsePage(fixture("positions-page.json"));
    assertThat(page.empty()).isFalse();
    assertThat(page.skipped()).isEqualTo(1);
    assertThat(page.positions()).hasSize(2);

    JumpitPosition first = page.positions().get(0);
    assertThat(first.id()).isEqualTo("55220505");
    assertThat(first.jobCategories()).containsExactly("서버/백엔드 개발자", "빅데이터 엔지니어");
    assertThat(first.techStacks()).containsExactly("Java", "Spring Boot", "ESET");
    assertThat(first.minCareer()).isEqualTo(3);
    assertThat(first.maxCareer()).isEqualTo(7);
    assertThat(first.location()).isEqualTo("서울 중구");
    assertThat(first.closedAt()).isEqualTo(LocalDate.of(2026, 11, 5));
  }

  @Test
  @DisplayName("상시 채용은 마감일이 없고 분류가 비면 빈 목록이다")
  void alwaysOpenHasNoDeadline() throws IOException {
    JumpitPosition second = parser.parsePage(fixture("positions-page.json")).positions().get(1);
    assertThat(second.closedAt()).isNull();
    assertThat(second.jobCategories()).isEmpty();
    assertThat(second.newcomer()).isTrue();
  }

  @Test
  @DisplayName("마지막 다음 페이지는 empty 다")
  void emptyPage() throws IOException {
    assertThat(parser.parsePage(fixture("positions-empty.json")).empty()).isTrue();
  }

  @Test
  @DisplayName("result.positions 가 없으면 구조 변경으로 본다")
  void brokenStructureThrows() {
    assertThatThrownBy(() -> parser.parsePage("{\"result\":{}}"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("상세에서 게시일을 읽는다")
  void parsesPublishedAt() throws IOException {
    assertThat(parser.parsePublishedAt(fixture("position-detail.json")))
        .contains(LocalDate.of(2026, 10, 7));
    assertThat(parser.parsePublishedAt("{\"result\":{}}")).isEmpty();
  }
}
