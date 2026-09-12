package com.carix.jobposting;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobPostingSkillRepository extends JpaRepository<JobPostingSkill, UUID> {

  /** 중복 스킬(dupId)과 대표 스킬(canonicalId)이 동일한 공고에 이미 둘 다 연결되어 있는 경우, 중복 스킬 연결 행을 먼저 삭제합니다. */
  @Modifying(clearAutomatically = true)
  @Query(
      """
      DELETE FROM JobPostingSkill jps
      WHERE jps.skill.id = :dupId
        AND jps.jobPosting.id IN (
          SELECT jps2.jobPosting.id FROM JobPostingSkill jps2 WHERE jps2.skill.id = :canonicalId
        )
      """)
  int deleteDuplicateLinks(@Param("dupId") UUID dupId, @Param("canonicalId") UUID canonicalId);

  /** 대표 스킬이 아직 연결되어 있지 않은 공고들의 스킬 참조를 중복 스킬(dupId)에서 대표 스킬(canonical)로 변경합니다. */
  @Modifying(clearAutomatically = true)
  @Query("UPDATE JobPostingSkill jps SET jps.skill = :canonical WHERE jps.skill.id = :dupId")
  int reassignSkill(@Param("dupId") UUID dupId, @Param("canonical") Skill canonical);
}
