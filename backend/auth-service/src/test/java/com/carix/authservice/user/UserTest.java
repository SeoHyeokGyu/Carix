package com.carix.authservice.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  void getter_및_온보딩완료여부_검증() {
    UUID id = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    User user = new User(id, "user@example.com", "hash", "COMPLETED", now, now);

    assertThat(user.getId()).isEqualTo(id);
    assertThat(user.getEmail()).isEqualTo("user@example.com");
    assertThat(user.getPasswordHash()).isEqualTo("hash");
    assertThat(user.getOnboardingStep()).isEqualTo("COMPLETED");
    assertThat(user.getCreatedAt()).isEqualTo(now);
    assertThat(user.getUpdatedAt()).isEqualTo(now);
    assertThat(user.isOnboardingComplete()).isTrue();

    User incomplete = new User(id, "user2@example.com", "hash2", "EXPENSE", now, now);
    assertThat(incomplete.isOnboardingComplete()).isFalse();
  }

  @Test
  void 기본_생성자_검증() throws Exception {
    Constructor<User> constructor = User.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    User user = constructor.newInstance();
    assertThat(user).isNotNull();
  }

  @Test
  void 온보딩_단계를_바꾸면_갱신시각도_함께_바뀐다() {
    OffsetDateTime created = OffsetDateTime.parse("2026-09-01T00:00:00Z");
    OffsetDateTime changed = OffsetDateTime.parse("2026-09-07T10:00:00Z");
    User user =
        new User(UUID.randomUUID(), "user@example.com", "hash", "PROFILE", created, created);

    user.changeOnboardingStep("INCOME", changed);

    assertThat(user.getOnboardingStep()).isEqualTo("INCOME");
    assertThat(user.getUpdatedAt()).isEqualTo(changed);
    assertThat(user.getCreatedAt()).isEqualTo(created);
  }
}
