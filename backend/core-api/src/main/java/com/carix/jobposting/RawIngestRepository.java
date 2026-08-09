package com.carix.jobposting;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawIngestRepository extends JpaRepository<RawIngest, UUID> {

  Optional<RawIngest> findBySourceAndSourceItemId(String source, String sourceItemId);
}
