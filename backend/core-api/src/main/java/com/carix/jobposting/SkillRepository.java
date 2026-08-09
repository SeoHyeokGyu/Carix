package com.carix.jobposting;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

  Optional<Skill> findByStandardNameAndCategory(String standardName, String category);
}
