package com.carix.authservice.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void 익명_사용자는_이메일도_비밀번호도_없다() {
    User user = User.anonymous();

    assertThat(user.getEmail()).isNull();
    assertThat(user.getPasswordHash()).isNull();
    assertThat(user.isRegistered()).isFalse();
    assertThat(user.getOnboardingStep()).isEqualTo("PROFILE");
  }

  @Test
  void 승격은_같은_행을_갱신한다_id_가_바뀌지_않아야_데이터가_이어진다() {
    User user = User.anonymous();
    var idBefore = user.getId();

    user.promote("beta@example.com", "$2a$10$hash");

    assertThat(user.getId()).isEqualTo(idBefore);
    assertThat(user.getEmail()).isEqualTo("beta@example.com");
    assertThat(user.getPasswordHash()).isEqualTo("$2a$10$hash");
    assertThat(user.isRegistered()).isTrue();
  }

  @Test
  void 이미_등록된_사용자는_다시_승격할_수_없다() {
    User user = User.anonymous();
    user.promote("beta@example.com", "$2a$10$hash");

    assertThatThrownBy(() -> user.promote("other@example.com", "$2a$10$other"))
        .isInstanceOf(IllegalStateException.class);
  }
}
