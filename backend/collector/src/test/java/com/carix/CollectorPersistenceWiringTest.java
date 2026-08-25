package com.carix;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.backfill.JobPostingBackfill;
import com.carix.wagestat.WageStatObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CollectorPersistenceWiringTest {

  @Autowired(required = false)
  private JobPostingRepository jobPostingRepository;

  @Autowired(required = false)
  private RawIngestRepository rawIngestRepository;

  @Autowired(required = false)
  private SkillRepository skillRepository;

  @Autowired(required = false)
  private JobPostingBackfill jobPostingBackfill;

  @Autowired(required = false)
  private WageStatObservationRepository wageStatObservationRepository;

  @Test
  void registersJobPostingRepositories() {
    assertNotNull(jobPostingRepository);
    assertNotNull(rawIngestRepository);
    assertNotNull(skillRepository);
    assertNotNull(jobPostingBackfill);
    assertNotNull(wageStatObservationRepository);
  }
}
