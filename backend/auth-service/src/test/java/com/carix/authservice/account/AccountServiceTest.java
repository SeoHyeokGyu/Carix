package com.carix.authservice.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.carix.authservice.user.User;
import com.carix.authservice.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AccountServiceTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-28T00:00:00Z");

  private UserRepository userRepository;
  private AccountService service;

  /** 이미 저장돼 있는 계정. 스터빙 인자 안에서 service 를 부르면 목 상호작용이 꼬인다. */
  private User existing() {
    return new User(UUID.randomUUID(), "beta@example.com", "$2a$10$hash", "PROFILE", NOW, NOW);
  }

  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    given(userRepository.save(any(User.class))).willAnswer(i -> i.getArgument(0));
    service = new AccountService(userRepository, new BCryptPasswordEncoder());
  }

  @Test
  void 가입하면_평문이_아니라_BCrypt_해시가_저장된다() {
    User user = service.register("beta@example.com", "password123");

    assertThat(user.getPasswordHash()).isNotEqualTo("password123").startsWith("$2");
  }

  @Test
  void 이메일은_정규화해_저장한다_케이스만_다른_계정이_갈리면_안_된다() {
    User user = service.register("  Beta@Example.COM  ", "password123");

    assertThat(user.getEmail()).isEqualTo("beta@example.com");
  }

  @Test
  void 이미_쓰이는_이메일이면_거부한다() {
    given(userRepository.findByEmail("beta@example.com")).willReturn(Optional.of(existing()));

    assertThatThrownBy(() -> service.register("BETA@example.com", "password123"))
        .isInstanceOf(EmailTakenException.class);
  }

  @Test
  void BCrypt_한계를_넘는_비밀번호는_조용히_자르지_않고_거부한다() {
    String tooLong = "가".repeat(25); // UTF-8 3바이트 × 25 = 75바이트
    assertThat(tooLong.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

    assertThatThrownBy(() -> service.register("beta@example.com", tooLong))
        .isInstanceOf(PasswordTooLongException.class);
  }

  @Test
  void 올바른_비밀번호면_사용자를_돌려준다() {
    User stored = service.register("beta@example.com", "password123");
    given(userRepository.findByEmail("beta@example.com")).willReturn(Optional.of(stored));

    assertThat(service.authenticate("beta@example.com", "password123")).isSameAs(stored);
  }

  @Test
  void 없는_이메일과_틀린_비밀번호는_같은_예외를_던진다() {
    User stored = service.register("beta@example.com", "password123");
    given(userRepository.findByEmail("beta@example.com")).willReturn(Optional.of(stored));
    given(userRepository.findByEmail("nobody@example.com")).willReturn(Optional.empty());

    assertThatThrownBy(() -> service.authenticate("nobody@example.com", "password123"))
        .isInstanceOf(InvalidCredentialsException.class);
    assertThatThrownBy(() -> service.authenticate("beta@example.com", "wrong-password"))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void 비밀번호가_없는_옛_계정으로는_로그인할_수_없다() {
    User legacy = new User(UUID.randomUUID(), "legacy@example.com", null, "PROFILE", NOW, NOW);
    given(userRepository.findByEmail("legacy@example.com")).willReturn(Optional.of(legacy));

    assertThatThrownBy(() -> service.authenticate("legacy@example.com", "anything"))
        .isInstanceOf(InvalidCredentialsException.class);
  }
}
