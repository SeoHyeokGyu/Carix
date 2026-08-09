package com.carix.jobposting.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.RawIngestRepository;
import com.carix.jobposting.SkillRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SeedLoaderTest {

  @Autowired
  private JobPostingRepository jobPostingRepository;

  @Autowired
  private SkillRepository skillRepository;

  @Autowired
  private RawIngestRepository rawIngestRepository;

  private Path verificationOutDir() {
    Path repoRoot = Path.of("").toAbsolutePath();
    while (repoRoot != null && !repoRoot.getFileName().toString().equals("Carix")) {
      repoRoot = repoRoot.getParent();
    }
    return repoRoot.resolve("verification/out");
  }

  @Test
  void loadsRealJobKoreaAndSaraminSeedDataIfPresent() throws Exception {
    Path dir = verificationOutDir();
    assumeTrue(Files.exists(dir.resolve("jobkorea_jobs.csv")),
        "verification/out/jobkorea_jobs.csv 없음 — 2026-08-08 세션 산출물이 필요");

    SeedLoader loader = new SeedLoader(jobPostingRepository, skillRepository, rawIngestRepository);
    int loaded = loader.load(dir);

    assertThat(loaded).isGreaterThan(0);

    List<JobPosting> jobKoreaPostings = jobPostingRepository.findAll().stream()
        .filter(p -> p.getSource().equals("JOBKOREA"))
        .toList();
    assertThat(jobKoreaPostings).isNotEmpty();
    assertThat(jobKoreaPostings).anySatisfy(p -> assertThat(p.getSkills()).isNotEmpty());
  }
}
