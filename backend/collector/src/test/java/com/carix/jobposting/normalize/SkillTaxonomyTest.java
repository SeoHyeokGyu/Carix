package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillAlias;
import com.carix.jobposting.SkillAliasRepository;
import com.carix.jobposting.SkillRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillTaxonomyTest {

  private final SkillTaxonomy taxonomy = new SkillTaxonomy();

  @Test
  void loadsDynamicallyFromRepositoriesWhenProvided() {
    SkillRepository skillRepo = mock(SkillRepository.class);
    SkillAliasRepository aliasRepo = mock(SkillAliasRepository.class);

    Skill rustSkill =
        Skill.builder().standardName("Rust").category("BACKEND").isCustom(false).build();
    SkillAlias rustAlias = SkillAlias.builder().skill(rustSkill).aliasName("러스트").build();

    given(skillRepo.findAll()).willReturn(List.of(rustSkill));
    given(aliasRepo.findAllWithSkill()).willReturn(List.of(rustAlias));

    SkillTaxonomy dynamicTaxonomy = new SkillTaxonomy(skillRepo, aliasRepo);

    assertEquals("Rust", dynamicTaxonomy.canonicalName("rust"));
    assertEquals("Rust", dynamicTaxonomy.canonicalName("러스트"));
    assertEquals(List.of("Rust"), dynamicTaxonomy.extractCanonicalSkills("Rust, 러스트"));
  }

  @Test
  void mergesCaseVariantsIntoOneStandardName() {
    assertEquals("Java", taxonomy.canonicalName("JAVA"));
    assertEquals("Java", taxonomy.canonicalName("Java"));
    assertEquals("Java", taxonomy.canonicalName("java"));
    assertEquals("jQuery", taxonomy.canonicalName("Jquery"));
    assertEquals("C#", taxonomy.canonicalName(".Net"));
  }

  @Test
  void splitsCompoundSkillStringsIntoIndividualCanonicalSkills() {
    // 쉼표 분리
    List<String> commaSeparated = taxonomy.extractCanonicalSkills("Java8, JavaScript");
    assertEquals(List.of("Java", "JavaScript"), commaSeparated);

    // 슬래시 분리
    List<String> slashSeparated = taxonomy.extractCanonicalSkills("JAVA/Spring");
    assertEquals(List.of("Java", "Spring Boot"), slashSeparated);

    // 복합 기호 분리
    List<String> multi = taxonomy.extractCanonicalSkills("JSP, HTML, CSS, javascrip");
    assertEquals(List.of("JSP", "HTML", "CSS", "JavaScript"), multi);

    // 괄호 및 점 결합 분리
    List<String> bracketAndDot = taxonomy.extractCanonicalSkills("JavaScript(TypeScript)");
    assertEquals(List.of("JavaScript", "TypeScript"), bracketAndDot);

    List<String> dotSeparated = taxonomy.extractCanonicalSkills("Kotlin Spring Boot. Java");
    assertEquals(List.of("Kotlin", "Spring Boot", "Java"), dotSeparated);
  }

  @Test
  void filtersOutSentenceLevelNoiseTags() {
    // 문장형 우대조건 태그에서 핵심 스킬(Java, Oracle)만 추출하거나 잡음 자체는 필터링
    List<String> extracted = taxonomy.extractCanonicalSkills("전산업무자격(JAVA,ORACLE)보유자 우대");
    assertEquals(List.of("Java", "Oracle"), extracted);

    // 순수 잡음 문장은 빈 리스트 반환
    List<String> pureNoise = taxonomy.extractCanonicalSkills("인근거주자 우대 / 운전가능자 필수");
    assertTrue(pureNoise.isEmpty());
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
    assertEquals("SKILL", taxonomy.category("Docker"));
  }

  @Test
  void categorizesByCanonicalNameSoCaseDoesNotMatter() {
    assertEquals("JOB_CATEGORY", taxonomy.category("웹개발 "));
    assertEquals("SKILL", taxonomy.category("java"));
  }
}
