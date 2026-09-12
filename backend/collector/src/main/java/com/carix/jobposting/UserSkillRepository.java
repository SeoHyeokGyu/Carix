package com.carix.jobposting;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {

  /** 중복 스킬(dupId)과 대표 스킬(canonicalId)을 동일한 사용자가 이미 둘 다 보유하고 있는 경우, 중복 스킬 연결 행을 삭제합니다. */
  @Modifying(clearAutomatically = true)
  @Query(
      """
      DELETE FROM UserSkill us
      WHERE us.skill.id = :dupId
        AND us.userId IN (
          SELECT us2.userId FROM UserSkill us2 WHERE us2.skill.id = :canonicalId
        )
      """)
  int deleteDuplicateLinks(@Param("dupId") UUID dupId, @Param("canonicalId") UUID canonicalId);

  /** 대표 스킬이 등록되어 있지 않은 사용자들의 보유 스킬을 중복 스킬(dupId)에서 대표 스킬(canonical)로 변경합니다. */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE UserSkill us SET us.skill = :canonical WHERE us.skill.id = :dupId")
  int reassignSkill(@Param("dupId") UUID dupId, @Param("canonical") Skill canonical);
}
