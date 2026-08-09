package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RawIngestRepositoryTest {

  @Autowired
  private RawIngestRepository rawIngestRepository;

  @Test
  void savesAndFindsBySourceAndSourceItemId() {
    RawIngest rawIngest = new RawIngest(
        "JOBKOREA", "49730351", "{\"html\":\"<html>raw</html>\"}",
        "v1", "202608", "202608", Instant.parse("2026-08-09T00:00:00Z"));

    rawIngestRepository.save(rawIngest);

    var found = rawIngestRepository.findBySourceAndSourceItemId("JOBKOREA", "49730351");
    assertThat(found).isPresent();
    assertThat(found.get().getPayload()).isEqualTo("{\"html\":\"<html>raw</html>\"}");
  }
}
