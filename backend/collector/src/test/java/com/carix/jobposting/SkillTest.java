package com.carix.jobposting;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillTest {

  @Test
  @DisplayName("생성자 및 Getter 정상 동작 확인")
  void testConstructorAndGetters() {
    Skill skill = new Skill("Java", "Backend", true);

    assertThat(skill.getId()).isNotNull();
    assertThat(skill.getStandardName()).isEqualTo("Java");
    assertThat(skill.getCategory()).isEqualTo("Backend");
    assertThat(skill.isCustom()).isTrue();
    assertThat(skill.getCreatedBy()).isNull();
  }

  @Test
  @DisplayName("recategorize 및 markAsMarketObserved 상태 변경 확인")
  void testMutations() {
    Skill skill = new Skill("Python", "Backend", true);

    skill.recategorize("Data / AI");
    assertThat(skill.getCategory()).isEqualTo("Data / AI");

    skill.markAsMarketObserved();
    assertThat(skill.isCustom()).isFalse();
  }

  @Test
  @DisplayName("Builder 패턴을 통한 Skill 생성 확인")
  void testBuilder() {
    UUID id = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Skill skill =
        Skill.builder()
            .id(id)
            .standardName("Docker")
            .category("DevOps")
            .isCustom(false)
            .createdBy(userId)
            .build();

    assertThat(skill.getId()).isEqualTo(id);
    assertThat(skill.getStandardName()).isEqualTo("Docker");
    assertThat(skill.getCategory()).isEqualTo("DevOps");
    assertThat(skill.isCustom()).isFalse();
    assertThat(skill.getCreatedBy()).isEqualTo(userId);
  }

  @Test
  @DisplayName("기본 생성자 리플렉션 테스트")
  void testProtectedNoArgsConstructor() throws Exception {
    Constructor<Skill> constructor = Skill.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    Skill skill = constructor.newInstance();
    assertThat(skill).isNotNull();
    assertThat(skill.getId()).isNotNull();
  }
}
