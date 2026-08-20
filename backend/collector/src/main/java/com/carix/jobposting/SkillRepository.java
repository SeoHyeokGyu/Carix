package com.carix.jobposting;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

  Optional<Skill> findByStandardNameAndCategory(String standardName, String category);

  /**
   * 어떤 공고도 참조하지 않는 수집 스킬. 표기 통합(`JAVA` -> `Java`)이나 분류 변경으로
   * 연결이 옮겨 가면 남는다. 사용자가 직접 만든 스킬은 참조가 없어도 지우지 않는다.
   */
  @Query("""
      SELECT s FROM Skill s
      WHERE s.createdBy IS NULL
        AND NOT EXISTS (SELECT 1 FROM JobPostingSkill jps WHERE jps.skill = s)
      """)
  List<Skill> findUnreferencedCrawledSkills();
}
