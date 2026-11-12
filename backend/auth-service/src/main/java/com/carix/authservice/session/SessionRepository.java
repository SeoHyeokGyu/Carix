package com.carix.authservice.session;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SessionRepository extends JpaRepository<Session, UUID> {

  Optional<Session> findByTokenHash(String tokenHash);

  void deleteByTokenHash(String tokenHash);

  void deleteByUserId(UUID userId);

  @Transactional
  @Modifying
  @Query("DELETE FROM Session s WHERE s.expiresAt < :now")
  int deleteExpired(@Param("now") OffsetDateTime now);
}
