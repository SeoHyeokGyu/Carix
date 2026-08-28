package com.carix.authservice.session;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "session")
public class Session {

  @Id private UUID id;

  private UUID userId;

  /** SHA-256 hex. 원본 토큰은 쿠키에만 있고 여기 저장하지 않는다. */
  private String tokenHash;

  private OffsetDateTime expiresAt;

  private OffsetDateTime createdAt;

  protected Session() {}

  public Session(
      UUID id, UUID userId, String tokenHash, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }
}
