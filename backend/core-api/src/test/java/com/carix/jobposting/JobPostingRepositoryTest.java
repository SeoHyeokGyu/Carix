package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobPostingRepositoryTest {

  @Autowired
  private JobPostingRepository jobPostingRepository;

  @Autowired
  private SkillRepository skillRepository;

  @Test
  void savesJobPostingWithExtendedColumnsAndLinkedSkill() {
    Skill skill = skillRepository.save(new Skill("Java", "SKILL", true));

    JobPosting posting = new JobPosting(
        "JOBKOREA", "49744087", "백엔드 개발자", "㈜테스트회사",
        4000L, 6000L, "서울 종로구", 2, null,
        "정규직", null, null, "주임~대리급 외", null, null,
        LocalDate.of(2026, 8, 6), LocalDate.of(2026, 8, 17),
        "202608", "202608", Instant.parse("2026-08-09T00:00:00Z"));
    posting.addSkill(skill);

    jobPostingRepository.save(posting);

    var found = jobPostingRepository.findBySourceAndExternalId("JOBKOREA", "49744087");
    assertThat(found).isPresent();
    assertThat(found.get().getSalaryMin()).isEqualTo(4000L);
    assertThat(found.get().getPositionLevel()).isEqualTo("주임~대리급 외");
    assertThat(found.get().getSkills()).hasSize(1);
  }
}
