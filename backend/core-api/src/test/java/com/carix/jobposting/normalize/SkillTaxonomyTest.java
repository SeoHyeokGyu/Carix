package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkillTaxonomyTest {

  private final SkillTaxonomy taxonomy = new SkillTaxonomy();

  @Test
  void mergesCaseVariantsIntoOneStandardName() {
    assertEquals("Java", taxonomy.canonicalName("JAVA"));
    assertEquals("Java", taxonomy.canonicalName("Java"));
    assertEquals("Java", taxonomy.canonicalName("java"));
    assertEquals("jQuery", taxonomy.canonicalName("Jquery"));
    assertEquals(".NET", taxonomy.canonicalName(".Net"));
  }

  @Test
  void keepsUnknownSkillNamesAsObserved() {
    assertEquals("eGovFrame(전자정부 프레임워크)", taxonomy.canonicalName("eGovFrame(전자정부 프레임워크)"));
    assertEquals("MQTT (통신 프로토콜)", taxonomy.canonicalName("  MQTT (통신 프로토콜)  "));
  }

  @Test
  void marksJobTitlesAsJobCategoryNotSkill() {
    assertEquals("JOB_CATEGORY", taxonomy.category("백엔드/서버개발"));
    assertEquals("JOB_CATEGORY", taxonomy.category("웹개발"));
    assertEquals("JOB_CATEGORY", taxonomy.category("프론트엔드"));
    assertEquals("JOB_CATEGORY", taxonomy.category("QA/테스터"));
    assertEquals("JOB_CATEGORY", taxonomy.category("데이터분석가"));
  }

  @Test
  void keepsTechnologyTermsAsSkill() {
    assertEquals("SKILL", taxonomy.category("Java"));
    assertEquals("SKILL", taxonomy.category("AWS"));
    assertEquals("SKILL", taxonomy.category("머신러닝"));
    assertEquals("SKILL", taxonomy.category("정보보안"));
  }

  @Test
  void categorizesByCanonicalNameSoCaseDoesNotMatter() {
    assertEquals("JOB_CATEGORY", taxonomy.category("웹개발 "));
    assertEquals("SKILL", taxonomy.category("java"));
  }
}
