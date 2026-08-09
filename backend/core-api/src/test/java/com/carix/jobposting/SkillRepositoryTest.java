package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SkillRepositoryTest {

  @Autowired
  private SkillRepository skillRepository;

  @Test
  void savesAndFindsByStandardNameAndCategory() {
    Skill skill = new Skill("Java", "SKILL", true);
    skillRepository.save(skill);

    var found = skillRepository.findByStandardNameAndCategory("Java", "SKILL");
    assertThat(found).isPresent();
    assertThat(found.get().getStandardName()).isEqualTo("Java");
  }

  @Test
  void treatsSameNameDifferentCategoryAsDistinctSkills() {
    skillRepository.save(new Skill("소프트웨어개발", "SKILL", true));
    skillRepository.save(new Skill("소프트웨어개발", "INDUSTRY", true));

    assertThat(skillRepository.findByStandardNameAndCategory("소프트웨어개발", "SKILL")).isPresent();
    assertThat(skillRepository.findByStandardNameAndCategory("소프트웨어개발", "INDUSTRY")).isPresent();
  }
}
