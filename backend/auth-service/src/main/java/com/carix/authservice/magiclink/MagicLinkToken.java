package com.carix.authservice.magiclink;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "magic_link_token")
public class MagicLinkToken {

  @Id private UUID id;

  private UUID userId;

  private String tokenHash;

  private OffsetDateTime expiresAt;

  private OffsetDateTime usedAt;

  protected MagicLinkToken() {}

  public MagicLinkToken(UUID id, UUID userId, String tokenHash, OffsetDateTime expiresAt) {
    this.id = id;
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
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

  public OffsetDateTime getUsedAt() {
    return usedAt;
  }

  public void markUsed(OffsetDateTime usedAt) {
    this.usedAt = usedAt;
  }
}
