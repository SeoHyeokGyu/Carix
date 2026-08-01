package com.carix.authservice.session;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "session")
public class Session {

	@Id
	private UUID id;

	private UUID userId;

	private String sessionToken;

	private OffsetDateTime expiresAt;

	private OffsetDateTime createdAt;

	protected Session() {
	}

	public Session(UUID id, UUID userId, String sessionToken, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.sessionToken = sessionToken;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getSessionToken() {
		return sessionToken;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
