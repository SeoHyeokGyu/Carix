package com.carix.skill.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSkillTest {

  @Test
  @DisplayName("UserSkill 생성 및 정상 업데이트")
  void testCreateAndUpdate() {
    UUID userId = UUID.randomUUID();
    Skill skill = Skill.createStandard("Java", "BACKEND");
    UserSkill userSkill = new UserSkill(userId, skill, 24, LocalDate.of(2026, 7, 1));

    assertThat(userSkill.getUserId()).isEqualTo(userId);
    assertThat(userSkill.getSkill()).isEqualTo(skill);
    assertThat(userSkill.getMonthsOfExperience()).isEqualTo(24);
    assertThat(userSkill.getLastUsedDate()).isEqualTo(LocalDate.of(2026, 7, 1));

    userSkill.update(36, LocalDate.of(2026, 8, 1));
    assertThat(userSkill.getMonthsOfExperience()).isEqualTo(36);
    assertThat(userSkill.getLastUsedDate()).isEqualTo(LocalDate.of(2026, 8, 1));
  }

  @Test
  @DisplayName("UserSkill 업데이트 유효성 검사 (1~600 범위 초과 또는 lastUsedDate null)")
  void testUpdateValidation() {
    UserSkill userSkill = new UserSkill(UUID.randomUUID(), null, 12, LocalDate.now());

    assertThatThrownBy(() -> userSkill.update(0, LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> userSkill.update(601, LocalDate.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> userSkill.update(12, null)).isInstanceOf(NullPointerException.class);
  }
}
