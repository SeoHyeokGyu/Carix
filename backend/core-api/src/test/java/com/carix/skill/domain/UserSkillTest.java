package com.carix.skill.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSkillTest {

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
