package com.carix.skill.repository;

import com.carix.skill.domain.Skill;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

  /** 표준 스킬(isCustom=false) 중 표준 스킬명 또는 별칭에 검색어가 포함된 스킬 검색 */
  @Query(
      "SELECT DISTINCT s FROM Skill s LEFT JOIN SkillAlias a ON a.skill = s "
          + "WHERE s.isCustom = false AND ("
          + "LOWER(s.standardName) LIKE LOWER(CONCAT('%', :query, '%')) OR "
          + "LOWER(a.aliasName) LIKE LOWER(CONCAT('%', :query, '%'))"
          + ")")
  List<Skill> searchStandardSkills(@Param("query") String query);

  /** 대소문자 무시 표준 스킬명 조회 */
  Optional<Skill> findByStandardNameIgnoreCaseAndIsCustomFalse(String standardName);

  /** 대소문자 무시 스킬명 조회 */
  Optional<Skill> findByStandardNameIgnoreCase(String standardName);

  /** 스킬명 및 카테고리 일치 조회 */
  Optional<Skill> findByStandardNameIgnoreCaseAndCategory(String standardName, String category);
}
