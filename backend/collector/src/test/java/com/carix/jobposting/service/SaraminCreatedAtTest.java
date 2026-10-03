package com.carix.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.seed.JobPostingDateParser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 사람인 목록의 {@code .job_date .date} 는 마감일이다. 그것을 등록일로도 읽으면 연도 보정이 등록일은 1년 전, 마감일은 올해로 갈라 정확히 365일 창이
 * 만들어진다(운영 809행 전부). 등록일은 {@code .job_sector .job_day} 에서 읽는다 — {@code SaraminCrawler} 와 같은 규약.
 */
class SaraminCreatedAtTest {

  private static Element item(String jobDay, String jobDate) {
    String html =
        "<div class=\"item_recruit\"><div class=\"area_job\">"
            + "<div class=\"job_date\"><span class=\"date\">"
            + jobDate
            + "</span></div>"
            + "<div class=\"job_sector\">"
            + (jobDay == null ? "" : "<span class=\"job_day\">" + jobDay + "</span>")
            + "</div></div></div>";
    return Jsoup.parse(html).selectFirst("div.item_recruit");
  }

  @Test
  @DisplayName("등록일은 job_day 에서 읽고 마감일(job_date)을 쓰지 않는다")
  void readsRegistrationDateNotDeadline() {
    assertThat(JobPostingIngestService.saraminCreatedAtRaw(item("등록일 26/08/20", "~ 09/15(화)")))
        .isEqualTo("26/08/20");
  }

  @Test
  @DisplayName("job_day 가 없으면 빈 문자열 — 마감일로 대신하지 않는다")
  void emptyWhenNoJobDay() {
    assertThat(JobPostingIngestService.saraminCreatedAtRaw(item(null, "~ 09/15(화)"))).isEmpty();
  }

  @Test
  @DisplayName("등록일·마감일이 실제 게시 기간이 되고 365일 창이 생기지 않는다")
  void windowIsRealPostingPeriod() {
    Element el = item("등록일 26/08/20", "~ 09/15(화)");
    Instant collectedAt = Instant.parse("2026-08-23T00:00:00Z");

    LocalDate created =
        JobPostingDateParser.parseCreatedAt(
            "SARAMIN", JobPostingIngestService.saraminCreatedAtRaw(el), collectedAt);
    LocalDate deadline = JobPostingDateParser.parseDeadline("SARAMIN", "~ 09/15(화)", collectedAt);

    assertThat(created).isEqualTo(LocalDate.of(2026, 8, 20));
    assertThat(deadline).isEqualTo(LocalDate.of(2026, 9, 15));
    assertThat(ChronoUnit.DAYS.between(created, deadline)).isEqualTo(26);
  }
}
