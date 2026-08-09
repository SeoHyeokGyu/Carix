package com.carix.jobposting;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

  Optional<JobPosting> findBySourceAndExternalId(String source, String externalId);
}
