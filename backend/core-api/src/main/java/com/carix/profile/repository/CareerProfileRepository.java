package com.carix.profile.repository;

import com.carix.profile.domain.CareerProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerProfileRepository extends JpaRepository<CareerProfile, UUID> {
  Optional<CareerProfile> findByUserId(UUID userId);
}
