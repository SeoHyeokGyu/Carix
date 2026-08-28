package com.carix.authservice.user;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Transactional
  public User findOrCreate(String email) {
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    return userRepository
        .findByEmail(normalizedEmail)
        .orElseGet(
            () -> {
              OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
              User user = new User(UUID.randomUUID(), normalizedEmail, null, "PROFILE", now, now);
              return userRepository.save(user);
            });
  }
}
