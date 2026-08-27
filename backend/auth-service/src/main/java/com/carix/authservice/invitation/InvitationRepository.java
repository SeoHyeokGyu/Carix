package com.carix.authservice.invitation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

  Optional<Invitation> findByEmail(String email);

  List<Invitation> findAllByOrderByCreatedAtAsc();
}
