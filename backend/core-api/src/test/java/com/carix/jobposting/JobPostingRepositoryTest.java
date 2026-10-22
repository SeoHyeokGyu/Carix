package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobPostingRepositoryTest {

  @Autowired private JobPostingRepository jobPostingRepository;

  private void save(String source, String sampledMonth) {
    save(source, sampledMonth, 50_000_000L);
  }

  private void save(String source, String sampledMonth, Long salary) {
    jobPostingRepository.saveAndFlush(
        JobPosting.builder()
            .id(UUID.randomUUID())
            .source(source)
            .sampledMonth(sampledMonth)
            .salaryMin(salary)
            .salaryMax(salary)
            .build());
  }

  @Test
  @DisplayName("findLatestSampledMonth - 표본 월이 없는 행을 무시하고 가장 최근 월을 돌려준다")
  void findLatestSampledMonth() {
    save("JOBKOREA", "202608");
    save("JOBKOREA", "202609");
    save("SARAMIN", null);

    assertThat(jobPostingRepository.findLatestSampledMonth("202610"))
        .isEqualTo(Optional.of("202609"));
  }

  @Test
  @DisplayName("findLatestSampledMonth - 상한(현재 월)을 넘는 깨진 표본 월은 무시한다")
  void findLatestSampledMonth_ignoresMonthAfterCeiling() {
    save("JOBKOREA", "202609");
    save("JOBKOREA", "202613");

    assertThat(jobPostingRepository.findLatestSampledMonth("202610"))
        .isEqualTo(Optional.of("202609"));
    assertThat(jobPostingRepository.findLatestSampledMonth("202609"))
        .isEqualTo(Optional.of("202609"));
  }

  @Test
  @DisplayName("findSampledSourceMonths - 범위 안의 (원천, 표본 월) 쌍을 중복 없이 돌려준다")
  void findSampledSourceMonths() {
    save("JOBKOREA", "202604");
    save("JOBKOREA", "202604");
    save("SARAMIN", "202606");
    save("JOBKOREA", "202610");
    save("SARAMIN", null);

    assertThat(jobPostingRepository.findSampledSourceMonths("202604", "202609"))
        .containsExactlyInAnyOrder(
            new SourceMonth("JOBKOREA", "202604"), new SourceMonth("SARAMIN", "202606"));
  }

  @Test
  @DisplayName("findLatestSampledMonth - 연봉 미공개 표본 행은 최신 월을 올리지 않는다")
  void findLatestSampledMonth_ignoresUndisclosedSalary() {
    save("JOBKOREA", "202609");
    save("WANTED", "202610", null);

    assertThat(jobPostingRepository.findLatestSampledMonth("202610"))
        .isEqualTo(Optional.of("202609"));
  }

  @Test
  @DisplayName("findSampledSourceMonths - 연봉 미공개 표본 행은 돌려주지 않고 상한 월은 포함한다")
  void findSampledSourceMonths_disclosedOnlyAndInclusiveUpperBound() {
    save("JOBKOREA", "202609");
    save("WANTED", "202609", null);
    save("WANTED", "202608", null);

    assertThat(jobPostingRepository.findSampledSourceMonths("202604", "202609"))
        .containsExactly(new SourceMonth("JOBKOREA", "202609"));
  }
}
