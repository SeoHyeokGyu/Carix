package com.carix.jobposting.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillTaxonomyKnownTest {

  private final SkillTaxonomy taxonomy = new SkillTaxonomy();

  @Test
  @DisplayName("사전에 없는 영문 식별자는 신기술 후보로 올리지 않는다")
  void dropsUnknownTech() {
    assertThat(taxonomy.matchKnownSkills("ESET")).isEmpty();
    assertThat(taxonomy.extractCanonicalSkills("ESET")).containsExactly("ESET");
  }
}
