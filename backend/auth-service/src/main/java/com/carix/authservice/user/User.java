package com.carix.authservice.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

  @Id private UUID id;

  /** 익명 사용자는 {@code null}. 상태 컬럼을 따로 두지 않고 이 값으로 익명/등록을 도출한다. */
  private String email;

  /** 익명 사용자는 {@code null}. BCrypt 해시다. */
  private String passwordHash;

  private String onboardingStep;

  private OffsetDateTime createdAt;

  private OffsetDateTime updatedAt;

  protected User() {}

  public User(
      UUID id,
      String email,
      String passwordHash,
      String onboardingStep,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.onboardingStep = onboardingStep;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  /** 가입 절차 없이 시작한 방문자. 이메일도 비밀번호도 없다. */
  public static User anonymous() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    return new User(UUID.randomUUID(), null, null, "PROFILE", now, now);
  }

  /**
   * 익명 사용자를 등록 사용자로 승격한다. 행을 새로 만들지 않으므로 익명일 때 넣은 데이터와 세션이 그대로 이어진다.
   *
   * @throws IllegalStateException 이미 등록된 사용자인 경우
   */
  public void promote(String normalizedEmail, String bcryptHash) {
    if (isRegistered()) {
      throw new IllegalStateException("이미 등록된 사용자입니다");
    }
    this.email = normalizedEmail;
    this.passwordHash = bcryptHash;
    this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
  }

  /** 이메일과 비밀번호 해시는 함께 있거나 함께 없다 (DB CHECK 제약과 같은 규칙). */
  public boolean isRegistered() {
    return email != null;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getOnboardingStep() {
    return onboardingStep;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public boolean isOnboardingComplete() {
    return "COMPLETED".equals(onboardingStep);
  }
}
