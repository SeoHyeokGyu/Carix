package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobPostingTest {

  @Test
  @DisplayName("disclosedSalary - 상/하한 연봉이 모두 존재하면 평균을 반환하고 100만 미만은 만원 단위로 보정한다")
  void disclosedSalary_정상계산() {
    JobPosting postingWon =
        JobPosting.builder().salaryMin(50_000_000L).salaryMax(70_000_000L).build();
    assertThat(postingWon.disclosedSalary()).isEqualTo(60_000_000L);

    JobPosting postingManwon = JobPosting.builder().salaryMin(5_000L).salaryMax(7_000L).build();
    assertThat(postingManwon.disclosedSalary()).isEqualTo(60_000_000L);

    JobPosting postingZero = JobPosting.builder().salaryMin(0L).salaryMax(0L).build();
    assertThat(postingZero.disclosedSalary()).isEqualTo(0L);

    JobPosting postingNullMin = JobPosting.builder().salaryMin(null).salaryMax(70_000_000L).build();
    assertThat(postingNullMin.disclosedSalary()).isNull();

    JobPosting postingNullMax = JobPosting.builder().salaryMin(50_000_000L).salaryMax(null).build();
    assertThat(postingNullMax.disclosedSalary()).isNull();
  }

  @Test
  @DisplayName("windowDays - 시작일과 마감일이 정상일 때 일수를 반환하고 역전되거나 누락 시 null 을 반환한다")
  void windowDays_계산() {
    LocalDate start = LocalDate.of(2026, 8, 1);
    LocalDate end = LocalDate.of(2026, 8, 31);

    JobPosting normal = JobPosting.builder().postedStartDate(start).postedEndDate(end).build();
    assertThat(normal.windowDays()).isEqualTo(30);
    assertThat(normal.isReversed()).isFalse();
    assertThat(normal.isOpenEnded()).isFalse();

    JobPosting reversed = JobPosting.builder().postedStartDate(end).postedEndDate(start).build();
    assertThat(reversed.windowDays()).isNull();
    assertThat(reversed.isReversed()).isTrue();
    assertThat(reversed.isOpenEnded()).isFalse();

    JobPosting openEnded = JobPosting.builder().postedStartDate(start).postedEndDate(null).build();
    assertThat(openEnded.windowDays()).isNull();
    assertThat(openEnded.isReversed()).isFalse();
    assertThat(openEnded.isOpenEnded()).isTrue();

    JobPosting noStart = JobPosting.builder().postedStartDate(null).postedEndDate(end).build();
    assertThat(noStart.windowDays()).isNull();
    assertThat(noStart.isReversed()).isFalse();
    assertThat(noStart.isOpenEnded()).isFalse();
  }

  @Test
  @DisplayName("sampledYearMonth - 표본 월이 없거나 형식이 틀리면 null")
  void sampledYearMonth_nullWhenAbsent() {
    assertThat(JobPosting.builder().sampledMonth(null).build().sampledYearMonth()).isNull();
    assertThat(JobPosting.builder().sampledMonth("2026-09").build().sampledYearMonth()).isNull();
  }
}
