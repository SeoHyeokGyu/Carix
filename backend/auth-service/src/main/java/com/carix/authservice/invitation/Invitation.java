package com.carix.authservice.invitation;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "invitation")
public class Invitation {

  @Id private UUID id;

  private String email;

  private String status;

  private OffsetDateTime expiresAt;

  private OffsetDateTime createdAt;

  protected Invitation() {}

  /**
   * 초대를 PENDING 으로 새로 만든다. 이메일은 호출부에서 정규화한 값을 받는다 —
   * {@code invitation.email} 이 UNIQUE 라 케이스가 다르면 같은 사람이 두 행이 된다.
   */
  static Invitation pending(String normalizedEmail, OffsetDateTime expiresAt) {
    OffsetDateTime now = OffsetDateTime.now();
    return new Invitation(UUID.randomUUID(), normalizedEmail, "PENDING", expiresAt, now);
  }

  public Invitation(
      UUID id, String email, String status, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
    this.id = id;
    this.email = email;
    this.status = status;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getStatus() {
    return status;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
