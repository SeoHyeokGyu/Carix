package com.carix.jobposting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

  Optional<JobPosting> findBySourceAndExternalId(String source, String externalId);

  List<JobPosting> findBySource(String source);

  long countBySource(String source);

  List<JobPosting> findAllByOrderByCollectedAtDesc(Pageable pageable);

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE JobPosting p SET p.salaryMin = p.salaryMin * 10000 WHERE p.salaryMin IS NOT NULL AND p.salaryMin > 0 AND p.salaryMin < 1000000")
  int normalizeSalaryMinUnits();

  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query(
      "UPDATE JobPosting p SET p.salaryMax = p.salaryMax * 10000 WHERE p.salaryMax IS NOT NULL AND p.salaryMax > 0 AND p.salaryMax < 1000000")
  int normalizeSalaryMaxUnits();
}
