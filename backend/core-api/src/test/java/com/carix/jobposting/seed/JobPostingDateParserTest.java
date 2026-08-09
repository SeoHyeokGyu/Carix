package com.carix.jobposting.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class JobPostingDateParserTest {

  private static final Instant COLLECTED_AT = Instant.parse("2026-08-09T10:00:00Z");

  @Test
  void parsesJobKoreaRelativeDaysAgo() {
    LocalDate result = JobPostingDateParser.parseCreatedAt("JOBKOREA", "3일 전 등록", COLLECTED_AT);
    assertThat(result).isEqualTo(LocalDate.of(2026, 8, 6));
  }

  @Test
  void parsesJobKoreaMonthDayWithoutYear() {
    LocalDate result = JobPostingDateParser.parseCreatedAt("JOBKOREA", "07/29(수) 등록", COLLECTED_AT);
    assertThat(result).isEqualTo(LocalDate.of(2026, 7, 29));
  }

  @Test
  void parsesSaraminYyMmDd() {
    LocalDate result = JobPostingDateParser.parseCreatedAt("SARAMIN", "26/07/13", COLLECTED_AT);
    assertThat(result).isEqualTo(LocalDate.of(2026, 7, 13));
  }

  @Test
  void parsesSaraminModifiedDatePrefix() {
    LocalDate result = JobPostingDateParser.parseCreatedAt("SARAMIN", "수정일 26/08/06", COLLECTED_AT);
    assertThat(result).isEqualTo(LocalDate.of(2026, 8, 6));
  }

  @Test
  void parsesDeadlineMonthDayWithoutYear() {
    LocalDate result = JobPostingDateParser.parseDeadline("JOBKOREA", "~08/17(월)", COLLECTED_AT);
    assertThat(result).isEqualTo(LocalDate.of(2026, 8, 17));
  }

  @Test
  void parsesDeadlineTodayAsCollectedDate() {
    LocalDate result = JobPostingDateParser.parseDeadline("JOBKOREA", "오늘마감", COLLECTED_AT);
    assertThat(result).isEqualTo(LocalDate.of(2026, 8, 9));
  }

  @Test
  void parsesDeadlineTomorrow() {
    LocalDate result = JobPostingDateParser.parseDeadline("SARAMIN", "내일마감", COLLECTED_AT);
    assertThat(result).isEqualTo(LocalDate.of(2026, 8, 10));
  }

  @Test
  void returnsNullForOpenEndedDeadlines() {
    assertThat(JobPostingDateParser.parseDeadline("JOBKOREA", "상시채용", COLLECTED_AT)).isNull();
    assertThat(JobPostingDateParser.parseDeadline("SARAMIN", "채용시", COLLECTED_AT)).isNull();
    assertThat(JobPostingDateParser.parseDeadline("SARAMIN", "진행예정", COLLECTED_AT)).isNull();
  }

  @Test
  void returnsNullForBlankInput() {
    assertThat(JobPostingDateParser.parseCreatedAt("JOBKOREA", "", COLLECTED_AT)).isNull();
    assertThat(JobPostingDateParser.parseCreatedAt("JOBKOREA", null, COLLECTED_AT)).isNull();
  }
}
