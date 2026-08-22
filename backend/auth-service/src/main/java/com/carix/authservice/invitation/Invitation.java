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
