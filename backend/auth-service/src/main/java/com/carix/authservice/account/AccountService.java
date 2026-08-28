package com.carix.authservice.account;

import com.carix.authservice.user.User;
import com.carix.authservice.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원가입과 비밀번호 검증. 세션 발급은 호출부가 한다 — 이 클래스는 계정만 다룬다. */
@Service
public class AccountService {

  /** BCrypt 입력 한계. 넘으면 조용히 잘리므로 저장 전에 막는다. */
  private static final int MAX_PASSWORD_BYTES = 72;

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public User register(String email, String rawPassword) {
    String normalizedEmail = normalize(email);
    ensureWithinBcryptLimit(rawPassword);

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new EmailTakenException();
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return userRepository.save(
        new User(
            UUID.randomUUID(),
            normalizedEmail,
            passwordEncoder.encode(rawPassword),
            "PROFILE",
            now,
            now));
  }

  /**
   * 이메일과 비밀번호를 대조한다.
   *
   * <p>이메일이 없어도 비밀번호가 틀려도 같은 예외를 던진다. 호출부가 둘을 구분해 응답하면 이메일 존재 여부가 드러난다.
   */
  public User authenticate(String email, String rawPassword) {
    User user =
        userRepository.findByEmail(normalize(email)).orElseThrow(InvalidCredentialsException::new);

    // 매직링크로 만들어져 비밀번호가 없는 행이 남아 있을 수 있다. 그런 계정으로는 로그인하지 않는다.
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    return user;
  }

  private void ensureWithinBcryptLimit(String rawPassword) {
    if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new PasswordTooLongException();
    }
  }

  /** 조회·저장에 같은 정규화를 쓴다. 안 맞추면 케이스만 다른 이메일이 별도 계정이 된다. */
  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
