package com.carix.jobposting.programmers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProgrammersParserTest {

  private final ProgrammersParser parser = new ProgrammersParser();

  @Test
  @DisplayName("목록 JSON에서 공고 필드와 메타데이터를 정상 파싱한다")
  void parsesListJson() {
    String json =
        """
        {
          "total_count": 120,
          "total_pages": 6,
          "job_positions": [
            {
              "id": 9328,
              "title": "서버 개발자",
              "company": {
                "id": 101,
                "name": "그렙"
              },
              "address": "서울 서초구",
              "min_career": 3,
              "max_career": 7,
              "position_tags": ["Java", "Spring Boot", "MySQL"],
              "start_at": "2026-03-01T00:00:00.000Z",
              "end_at": "2026-11-30T23:59:59.000Z",
              "active": true
            },
            {
              "id": "",
              "title": "번호 없는 공고"
            }
          ]
        }
        """;

    ProgrammersParser.Page page = parser.parsePage(json);
    assertThat(page.empty()).isFalse();
    assertThat(page.totalCount()).isEqualTo(120);
    assertThat(page.totalPages()).isEqualTo(6);
    assertThat(page.skipped()).isEqualTo(1);
    assertThat(page.positions()).hasSize(1);

    ProgrammersPosition p = page.positions().get(0);
    assertThat(p.id()).isEqualTo("9328");
    assertThat(p.title()).isEqualTo("서버 개발자");
    assertThat(p.companyName()).isEqualTo("그렙");
    assertThat(p.address()).isEqualTo("서울 서초구");
    assertThat(p.minCareer()).isEqualTo(3);
    assertThat(p.maxCareer()).isEqualTo(7);
    assertThat(p.positionTags()).containsExactly("Java", "Spring Boot", "MySQL");
    assertThat(p.startAt()).isEqualTo(LocalDate.of(2026, 3, 1));
    assertThat(p.endAt()).isEqualTo(LocalDate.of(2026, 11, 30));
    assertThat(p.active()).isTrue();
  }

  @Test
  @DisplayName("카멜케이스 응답 형식도 정상 파싱한다")
  void parsesCamelCaseJson() {
    String json =
        """
        {
          "totalCount": 50,
          "totalPages": 3,
          "jobPositions": [
            {
              "id": 1001,
              "title": "프론트엔드 개발자",
              "companyName": "테크스타트업",
              "address": "경기도 성남시 판교",
              "minCareer": 1,
              "maxCareer": 5,
              "positionTags": ["React", "TypeScript"],
              "startAt": "2026-04-01",
              "endAt": "2026-05-01",
              "active": true
            }
          ]
        }
        """;

    ProgrammersParser.Page page = parser.parsePage(json);
    assertThat(page.positions()).hasSize(1);
    ProgrammersPosition p = page.positions().get(0);
    assertThat(p.id()).isEqualTo("1001");
    assertThat(p.companyName()).isEqualTo("테크스타트업");
    assertThat(p.positionTags()).containsExactly("React", "TypeScript");
  }

  @Test
  @DisplayName("JSON 형식이 깨져있으면 예외가 발생한다")
  void throwsOnMalformedJson() {
    assertThatThrownBy(() -> parser.parsePage("{invalid-json"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JSON 으로 읽지 못했습니다");
  }

  @Test
  @DisplayName("9000년 이상 연도(상시채용 플레이스홀더) 및 유효하지 않은 날짜는 null 로 처리한다")
  void handlesSpecialDates() {
    String json =
        """
        {
          "job_positions": [
            {
              "id": "2001",
              "title": "상시 채용 공고",
              "start_at": "invalid-date",
              "end_at": "9999-12-31T23:59:59.000Z"
            },
            {
              "id": "2002",
              "title": "날짜 누락 공고",
              "start_at": "short",
              "end_at": null
            }
          ]
        }
        """;

    ProgrammersParser.Page page = parser.parsePage(json);
    assertThat(page.positions()).hasSize(2);

    ProgrammersPosition p1 = page.positions().get(0);
    assertThat(p1.startAt()).isNull();
    assertThat(p1.endAt()).isNull();

    ProgrammersPosition p2 = page.positions().get(1);
    assertThat(p2.startAt()).isNull();
    assertThat(p2.endAt()).isNull();
  }

  @Test
  @DisplayName("태그가 객체 배열 형식이거나 공백이 포함된 경우 정상 정제한다")
  void handlesObjectTagsAndWhitespace() {
    String json =
        """
        {
          "job_positions": [
            {
              "id": "3001",
              "title": "풀스택 개발자",
              "position_tags": [
                {"name": " Java "},
                {"name": "   "},
                " Vue.js "
              ],
              "job_categories": [
                {"name": " 웹 풀스택 "}
              ],
              "min_career": "not-a-number",
              "max_career": null
            }
          ]
        }
        """;

    ProgrammersParser.Page page = parser.parsePage(json);
    ProgrammersPosition p = page.positions().get(0);
    assertThat(p.positionTags()).containsExactly("Java", "Vue.js");
    assertThat(p.jobCategories()).containsExactly("웹 풀스택");
    assertThat(p.minCareer()).isNull();
    assertThat(p.maxCareer()).isNull();
  }

  @Test
  @DisplayName("company 객체 없이 companyName 필드만 있거나 회사명이 아예 없는 경우도 처리한다")
  void handlesMissingCompanyName() {
    String json =
        """
        {
          "job_positions": [
            {
              "id": "4001",
              "title": "익명 회사 공고"
            }
          ]
        }
        """;

    ProgrammersParser.Page page = parser.parsePage(json);
    assertThat(page.positions().get(0).companyName()).isEmpty();
  }

  @Test
  @DisplayName("job_positions 가 없으면 예외가 발생한다")
  void throwsWhenNoJobPositions() {
    assertThatThrownBy(() -> parser.parsePage("{\"result\":{}}"))
        .isInstanceOf(IllegalStateException.class);
  }
}
