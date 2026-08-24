package com.carix.skill.repository;

import com.carix.skill.domain.SkillAlias;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, UUID> {

  Optional<SkillAlias> findByAliasNameIgnoreCase(String aliasName);

  List<SkillAlias> findBySkillId(UUID skillId);
}
