package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.normalize.PostingSalary;
import com.carix.jobposting.normalize.SalaryRange;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JobPostingSalaryTest {

  private static JobPosting posting(Long min, Long max) {
    return JobPosting.builder()
        .source("JOBKOREA")
        .externalId("1")
        .title("t")
        .salaryMin(min)
        .salaryMax(max)
        .collectedAt(Instant.now())
        .build();
  }

  @Test
  void newPostingIsUnknown() {
    assertThat(posting(null, null).getSalaryDisclosure()).isEqualTo("UNKNOWN");
  }

  @Test
  void bandClearsPublicSalary() {
    JobPosting p = posting(25_900_000L, 100_000_000L);

    p.applySalary(PostingSalary.band(new SalaryRange(25_900_000L, 100_000_000L)));

    assertThat(p.getSalaryMin()).isNull();
    assertThat(p.getSalaryMax()).isNull();
    assertThat(p.getSalaryBandMin()).isEqualTo(25_900_000L);
    assertThat(p.getSalaryDisclosure()).isEqualTo("BAND");
  }

  @Test
  void publicClearsBand() {
    JobPosting p = posting(null, null);
    p.applySalary(PostingSalary.band(new SalaryRange(30_000_000L, 40_000_000L)));

    p.applySalary(PostingSalary.publicRange(45_120_000L, 45_120_000L));

    assertThat(p.getSalaryMin()).isEqualTo(45_120_000L);
    assertThat(p.getSalaryBandMin()).isNull();
    assertThat(p.getSalaryDisclosure()).isEqualTo("PUBLIC");
  }

  @Test
  void unreachableKeepsOldValueAsBand() {
    JobPosting p = posting(40_000_000L, 70_000_000L);

    p.markSalaryUnreachable();

    assertThat(p.getSalaryMin()).isNull();
    assertThat(p.getSalaryBandMin()).isEqualTo(40_000_000L);
    assertThat(p.getSalaryBandMax()).isEqualTo(70_000_000L);
    assertThat(p.getSalaryDisclosure()).isEqualTo("UNKNOWN");
  }
}
