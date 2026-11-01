package com.carix.authservice.account;

import com.carix.authservice.session.SessionRepository;
import com.carix.authservice.user.User;
import com.carix.authservice.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
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
  private final SessionRepository sessionRepository;
  private final PasswordEncoder passwordEncoder;

  public AccountService(
      UserRepository userRepository,
      SessionRepository sessionRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.sessionRepository = sessionRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /** 게스트 행을 만든다. 이메일·비밀번호가 없어 로그인으로는 찾을 수 없다. */
  @Transactional
  public User createGuest() {
    return userRepository.save(User.guest(UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC)));
  }

  public Optional<User> findUser(UUID userId) {
    return userRepository.findById(userId);
  }

  public Optional<User> findGuest(UUID userId) {
    return userRepository.findById(userId).filter(User::isGuest);
  }

  /**
   * 가입. {@code currentUserId} 가 게스트이면 새 행을 만들지 않고 그 행을 정식 사용자로 전환한다 — 게스트 때 쌓은 데이터가 같은 id 로 이어진다.
   * 이메일이 이미 쓰이면 게스트 행은 바뀌지 않는다.
   *
   * <p>전환하면 그 사용자의 옛 24시간 세션을 같은 트랜잭션에서 모두 폐기한다. 따로 지우면 전환만 커밋되고 폐기가 실패해 옛 토큰이 정식 계정에 남는다.
   */
  @Transactional
  public RegistrationResult register(
      String email, String rawPassword, Optional<UUID> currentUserId) {
    String normalizedEmail = normalize(email);
    ensureWithinBcryptLimit(rawPassword);

    if (userRepository.findByEmail(normalizedEmail).isPresent()) {
      throw new EmailTakenException();
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String passwordHash = passwordEncoder.encode(rawPassword);

    Optional<User> guest = currentUserId.flatMap(this::findGuest);
    if (guest.isPresent()) {
      User user = guest.get();
      user.upgrade(normalizedEmail, passwordHash, now);
      sessionRepository.deleteByUserId(user.getId());
      return new RegistrationResult(userRepository.save(user), true);
    }
    return new RegistrationResult(
        userRepository.save(
            new User(UUID.randomUUID(), normalizedEmail, passwordHash, "PROFILE", now, now)),
        false);
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
