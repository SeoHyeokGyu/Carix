package com.carix.pipeline.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class JdbcDataHealthReaderSourceTest {

  @Autowired private JobPostingRepository repository;
  @Autowired private JdbcDataHealthReader reader;

  private void sampled(String source, String id, String company) {
    repository.save(
        JobPosting.builder()
            .source(source)
            .externalId(id)
            .title("t" + id)
            .companyName(company)
            .firstSeenMonth("202609")
            .lastSeenMonth("202609")
            .sampledMonth("202609")
            .collectedAt(Instant.now())
            .build());
  }

  @Test
  @DisplayName("같은 달 표본을 원천별로 나눠 센다 — 잡코리아 2, 원티드 3")
  void splitsSampleCountsBySource() {
    sampled("JOBKOREA", "1", "가");
    sampled("JOBKOREA", "2", "나");
    sampled("WANTED", "11", "다");
    sampled("WANTED", "12", "라");
    sampled("WANTED", "13", "마");
    repository.flush();

    var all = repository.findAll();
    var jobKorea = reader.sampleSeries("JOBKOREA", all);
    var wanted = reader.sampleSeries("WANTED", all);

    assertThat(jobKorea.sampledByMonth()).containsEntry("202609", 2);
    assertThat(jobKorea.cappedByMonth()).containsEntry("202609", 2);
    assertThat(wanted.sampledByMonth()).containsEntry("202609", 3);
    assertThat(wanted.cappedByMonth()).containsEntry("202609", 3);
  }
}
