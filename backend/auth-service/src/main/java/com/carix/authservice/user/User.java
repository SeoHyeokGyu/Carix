package com.carix.authservice.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

	@Id
	private UUID id;

	private String email;

	private String onboardingStep;

	private OffsetDateTime createdAt;

	private OffsetDateTime updatedAt;

	protected User() {
	}

	public User(UUID id, String email, String onboardingStep, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		this.id = id;
		this.email = email;
		this.onboardingStep = onboardingStep;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
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
