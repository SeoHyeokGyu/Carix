package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillAliasTest {

  @Test
  void skillAlias_getters() {
    Skill skill = new Skill("Java", "BACKEND", false);
    SkillAlias alias = new SkillAlias(skill, "JDK");

    assertThat(alias.getSkill()).isEqualTo(skill);
    assertThat(alias.getAliasName()).isEqualTo("JDK");
    assertThat(alias.getId()).isNotNull();
  }
}
