package com.carix.jobposting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobPostingSkillSyncTest {

  private final Skill java = new Skill("Java", "SKILL", false);
  private final Skill python = new Skill("Python", "SKILL", false);
  private final Skill webDevelopment = new Skill("웹개발", "JOB_CATEGORY", false);

  private JobPosting posting() {
    return new JobPosting(
        "JOBKOREA",
        "1",
        "제목",
        "회사",
        null,
        null,
        "서울 강남구",
        3,
        null,
        "정규직",
        null,
        null,
        null,
        null,
        null,
        null,
        "202608",
        "202608",
        Instant.EPOCH);
  }

  @Test
  void dropsSkillsThatAreNoLongerObserved() {
    JobPosting posting = posting();
    posting.addSkill(java);
    posting.addSkill(webDevelopment);

    posting.replaceSkills(List.of(java));

    assertEquals(List.of(java), posting.getSkills());
  }

  @Test
  void addsNewlyObservedSkills() {
    JobPosting posting = posting();
    posting.addSkill(java);

    posting.replaceSkills(List.of(java, python));

    assertTrue(posting.getSkills().containsAll(List.of(java, python)));
    assertEquals(2, posting.getSkills().size());
  }

  /**
   * `(job_posting_id, skill_id)` 유니크 제약이 있어, 남을 연결을 지웠다가 다시 넣으면 같은 플러시 안에서 삽입이 삭제보다 먼저 나가 제약 위반이
   * 난다. 유지되는 연결은 그대로 둬야 한다.
   */
  @Test
  void keepsExistingLinkInsteadOfRecreatingIt() {
    JobPosting posting = posting();
    posting.addSkill(java);
    Skill linkedBefore = posting.getSkills().getFirst();

    posting.replaceSkills(List.of(java, python));

    assertSame(linkedBefore, posting.getSkills().getFirst());
  }

  @Test
  void clearsEverythingWhenNothingIsObserved() {
    JobPosting posting = posting();
    posting.addSkill(java);
    posting.addSkill(python);

    posting.replaceSkills(List.of());

    assertTrue(posting.getSkills().isEmpty());
  }
}
