package com.carix.jobposting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, UUID> {

  @Query("SELECT a FROM SkillAlias a JOIN FETCH a.skill")
  List<SkillAlias> findAllWithSkill();
}
