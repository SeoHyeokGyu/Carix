package com.carix.jobposting.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillTaxonomyKnownTest {

  private final SkillTaxonomy taxonomy = new SkillTaxonomy();

  @Test
  @DisplayName("사전에 있는 스킬은 표준 이름으로 돌려준다")
  void returnsCanonicalForKnown() {
    assertThat(taxonomy.matchKnownSkills("Spring Boot")).containsExactly("Spring Boot");
    assertThat(taxonomy.matchKnownSkills("java")).containsExactly("Java");
  }

  @Test
  @DisplayName("사전에 없는 영문 식별자는 신기술 후보로 올리지 않는다")
  void dropsUnknownTech() {
    assertThat(taxonomy.matchKnownSkills("ESET")).isEmpty();
    assertThat(taxonomy.extractCanonicalSkills("ESET")).containsExactly("ESET");
  }
}
