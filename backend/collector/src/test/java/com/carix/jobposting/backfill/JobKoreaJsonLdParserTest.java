package com.carix.jobposting.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobKoreaJsonLdParserTest {

  private final JobKoreaJsonLdParser parser = new JobKoreaJsonLdParser();

  private static String page(String json) {
    return "<html><head><script type=\"application/ld+json\">"
        + json
        + "</script></head><body></body></html>";
  }

  @Test
  @DisplayName("JobPosting JSON-LD 에서 제목·회사·게시일·마감일·고용형태·경력·지역을 읽는다")
  void parsesJobPosting() {
    String html =
        page(
            """
            {"@context":"https://schema.org","@type":"JobPosting",
             "title":"Java 백엔드 개발자","datePosted":"2026-07-02",
             "validThrough":"2026-08-01T23:59","employmentType":"FULL_TIME",
             "experienceRequirements":"경력 3년↑",
             "hiringOrganization":{"@type":"Organization","name":"카릭스"},
             "jobLocation":{"@type":"Place","address":{"@type":"PostalAddress",
               "streetAddress":"서울 강남구 테헤란로 1"}}}
            """);

    JobKoreaJsonLd ld = parser.parse(html).orElseThrow();

    assertThat(ld.title()).isEqualTo("Java 백엔드 개발자");
    assertThat(ld.companyName()).isEqualTo("카릭스");
    assertThat(ld.datePosted()).isEqualTo(LocalDate.of(2026, 7, 2));
    assertThat(ld.validThrough()).isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(ld.employmentType()).isEqualTo("FULL_TIME");
    assertThat(ld.experienceText()).isEqualTo("경력 3년↑");
    assertThat(ld.region()).isEqualTo("서울 강남구");
  }

  @Test
  @DisplayName("배열 안의 JobPosting 도 찾고 다른 타입은 건너뛴다")
  void findsJobPostingInArray() {
    String html =
        page(
            """
            [{"@type":"BreadcrumbList"},
             {"@type":"JobPosting","title":"데이터 엔지니어","datePosted":"2025-11-03"}]
            """);

    assertThat(parser.parse(html).orElseThrow().title()).isEqualTo("데이터 엔지니어");
  }

  @Test
  @DisplayName("게시일이 없거나 JobPosting 이 없으면 비운다 — 월을 배정할 수 없다")
  void emptyWithoutDatePosted() {
    assertThat(parser.parse(page("{\"@type\":\"JobPosting\",\"title\":\"x\"}"))).isEmpty();
    assertThat(parser.parse(page("{\"@type\":\"Organization\"}"))).isEmpty();
    assertThat(parser.parse("<html></html>")).isEmpty();
    assertThat(parser.parse(page("{broken"))).isEmpty();
  }
}
