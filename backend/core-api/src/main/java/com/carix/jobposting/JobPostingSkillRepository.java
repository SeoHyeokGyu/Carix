package com.carix.jobposting;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostingSkillRepository extends JpaRepository<JobPostingSkill, UUID> {

  @Query(
      "SELECT jps FROM JobPostingSkill jps JOIN FETCH jps.skill WHERE jps.jobPosting.id IN :postingIds")
  List<JobPostingSkill> findByJobPostingIdIn(@Param("postingIds") Collection<UUID> postingIds);

  @Query("SELECT jps FROM JobPostingSkill jps WHERE jps.skill.id = :skillId")
  List<JobPostingSkill> findBySkillId(@Param("skillId") UUID skillId);
}
