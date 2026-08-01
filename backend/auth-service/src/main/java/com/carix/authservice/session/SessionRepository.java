package com.carix.authservice.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {

	Optional<Session> findBySessionToken(String sessionToken);

	void deleteBySessionToken(String sessionToken);

	void deleteByUserId(UUID userId);
}
