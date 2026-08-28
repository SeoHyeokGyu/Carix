package com.carix.authservice.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {

  Optional<Session> findByTokenHash(String tokenHash);

  void deleteByTokenHash(String tokenHash);

  void deleteByUserId(UUID userId);
}
