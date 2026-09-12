package com.carix.jobposting;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillAliasRepository extends JpaRepository<SkillAlias, UUID> {

  @Query("SELECT a FROM SkillAlias a JOIN FETCH a.skill")
  List<SkillAlias> findAllWithSkill();

  @Modifying(clearAutomatically = true)
  @Query("UPDATE SkillAlias sa SET sa.skill = :canonical WHERE sa.skill.id = :dupId")
  int reassignSkill(@Param("dupId") UUID dupId, @Param("canonical") Skill canonical);
}
