package com.carix.authservice.magiclink;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MagicLinkTokenRepository extends JpaRepository<MagicLinkToken, UUID> {

  Optional<MagicLinkToken> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<MagicLinkToken> findWithLockByTokenHash(String tokenHash);
}
