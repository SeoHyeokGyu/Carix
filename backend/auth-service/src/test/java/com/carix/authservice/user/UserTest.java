package com.carix.authservice.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  // FIX 1 (Critical, 최종 리뷰) — 뒤로 가는 전환은 거부한다. OnboardingController 의
  // 2차 방어선이 실제로 여기, 엔티티 안에 있다는 것을 컨트롤러 없이도 직접 확인한다.
  @Test
  void 뒤로_가는_전환은_예외를_던지고_값을_바꾸지_않는다() {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-07T00:00:00Z");
    User user = new User(UUID.randomUUID(), "user@example.com", "hash", "REVIEW", now, now);
    OffsetDateTime later = now.plusMinutes(1);

    assertThatThrownBy(() -> user.changeOnboardingStep("PROFILE", later))
        .isInstanceOf(OnboardingStepRegressionException.class);

    // 실패한 전환은 상태를 바꾸지 않는다 — 예외를 던지기 전에 필드를 먼저 쓰면
    // 부분 갱신이 남는다.
    assertThat(user.getOnboardingStep()).isEqualTo("REVIEW");
    assertThat(user.getUpdatedAt()).isEqualTo(now);
  }

  // 같은 단계로의 재전환은 뒤로 가는 것이 아니다 — 완료 후 재진입이 허용된 검토
  // 화면(guardOnboarding)의 정상 재제출이 이 경로를 탄다. 막으면 멀쩡한 재제출이
  // 예외를 받는다.
  @Test
  void 같은_단계로의_재전환은_허용한다() {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-07T00:00:00Z");
    User user = new User(UUID.randomUUID(), "user@example.com", "hash", "COMPLETED", now, now);
    OffsetDateTime later = now.plusMinutes(1);

    user.changeOnboardingStep("COMPLETED", later);

    assertThat(user.getOnboardingStep()).isEqualTo("COMPLETED");
    assertThat(user.getUpdatedAt()).isEqualTo(later);
  }

  // 손상된(STEP_ORDER 밖의) 현재 값은 복구할 수 있어야 한다 — indexOf 가 -1 을
  // 돌려줘 어떤 전환이든 "뒤가 아님"으로 통과한다(의도적, User#changeOnboardingStep
  // 주석 참고).
  @Test
  void 손상된_현재값도_알려진_단계로_되돌릴_수_있다() {
    OffsetDateTime now = OffsetDateTime.parse("2026-09-07T00:00:00Z");
    User user = new User(UUID.randomUUID(), "user@example.com", "hash", "GARBAGE", now, now);
    OffsetDateTime later = now.plusMinutes(1);

    user.changeOnboardingStep("PROFILE", later);

    assertThat(user.getOnboardingStep()).isEqualTo("PROFILE");
  }
}
