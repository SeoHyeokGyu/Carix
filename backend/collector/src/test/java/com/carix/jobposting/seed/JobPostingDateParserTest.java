package com.carix.jobposting.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobPostingDateParserTest {

  private final Instant now = Instant.parse("2026-08-15T12:00:00Z");

  @Test
  @DisplayName("parseCreatedAt - 다양한 등록일 형식 파싱")
  void parseCreatedAt() {
    assertThat(JobPostingDateParser.parseCreatedAt("JOBKOREA", null, now)).isNull();
    assertThat(JobPostingDateParser.parseCreatedAt("JOBKOREA", "", now)).isNull();
    assertThat(JobPostingDateParser.parseCreatedAt("JOBKOREA", "3일 전", now))
        .isEqualTo(LocalDate.of(2026, 8, 12));
    assertThat(JobPostingDateParser.parseCreatedAt("SARAMIN", "26/08/01", now))
        .isEqualTo(LocalDate.of(2026, 8, 1));
    assertThat(JobPostingDateParser.parseCreatedAt("SARAMIN", "08/10", now))
        .isEqualTo(LocalDate.of(2026, 8, 10));
    assertThat(JobPostingDateParser.parseCreatedAt("SARAMIN", "09/10", now))
        .isEqualTo(LocalDate.of(2025, 9, 10));
    assertThat(JobPostingDateParser.parseCreatedAt("SARAMIN", "알수없음", now)).isNull();
  }

  @Test
  @DisplayName("parseDeadline - 마감일 키워드 및 날짜 파싱")
  void parseDeadline() {
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", null, now)).isNull();
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "", now)).isNull();
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "오늘마감", now))
        .isEqualTo(LocalDate.of(2026, 8, 15));
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "내일마감", now))
        .isEqualTo(LocalDate.of(2026, 8, 16));
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "모레마감", now))
        .isEqualTo(LocalDate.of(2026, 8, 17));
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "상시채용", now)).isNull();
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "채용시 마감", now)).isNull();
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "진행예정", now)).isNull();
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "~ 08/20(목)", now))
        .isEqualTo(LocalDate.of(2026, 8, 20));
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "~ 07/20", now))
        .isEqualTo(LocalDate.of(2027, 7, 20));
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "기타", now)).isNull();
  }
}
