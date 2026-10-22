package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JobPostingCollectedViaTest {

  private static JobPosting posting() {
    return new JobPosting(
        "SARAMIN",
        "1",
        "백엔드",
        "회사",
        null,
        null,
        "서울",
        null,
        null,
        "UNKNOWN",
        "UNKNOWN",
        null,
        null,
        null,
        null,
        null,
        "202610",
        "202610",
        Instant.now());
  }

  private static Skill skill(String name) {
    Skill s = new Skill(name, "SKILL", false);
    ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
    return s;
  }

  @Test
  @DisplayName("확인 경로는 쉼표로 쌓이고 같은 값은 한 번만 남는다")
  void collectedViaAccumulatesWithoutDuplicates() {
    JobPosting p = posting();
    p.addCollectedVia(CollectedVia.SARAMIN_SEARCH);
    p.addCollectedVia(CollectedVia.JUMPIT);
    p.addCollectedVia(CollectedVia.JUMPIT);
    assertThat(p.getCollectedVia()).isEqualTo("SARAMIN_SEARCH,JUMPIT");
    assertThat(p.hasCollectedVia(CollectedVia.JUMPIT)).isTrue();
    assertThat(p.hasCollectedVia(CollectedVia.JOBKOREA_LIST)).isFalse();
  }

  @Test
  @DisplayName("스킬 더하기는 기존 연결을 지우지 않는다")
  void addSkillsIfAbsentKeepsExisting() {
    JobPosting p = posting();
    Skill java = skill("Java");
    p.addSkill(java);
    p.addSkillsIfAbsent(List.of(java, skill("Kotlin")));
    assertThat(p.getSkills()).extracting(Skill::getStandardName).containsExactly("Java", "Kotlin");
  }

  @Test
  @DisplayName("직무·경력은 비어 있을 때만 채운다")
  void fillsOnlyWhenEmpty() {
    JobPosting p = posting();
    p.fillJobFamilyIfEmpty("DATA");
    p.fillJobFamilyIfEmpty("AI");
    p.fillExperienceIfEmpty(3, 5);
    p.fillExperienceIfEmpty(0, 1);
    assertThat(p.getJobFamily()).isEqualTo("DATA");
    assertThat(p.getExperienceMin()).isEqualTo(3);
    assertThat(p.getExperienceMax()).isEqualTo(5);
  }
}
