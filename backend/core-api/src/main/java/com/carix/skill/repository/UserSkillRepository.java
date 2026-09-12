package com.carix.skill.repository;

import com.carix.skill.domain.UserSkill;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {

  @Query("SELECT us FROM UserSkill us JOIN FETCH us.skill WHERE us.userId = :userId")
  List<UserSkill> findByUserId(@Param("userId") UUID userId);

  @Query(
      "SELECT us FROM UserSkill us JOIN FETCH us.skill WHERE us.id = :id AND us.userId = :userId")
  Optional<UserSkill> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

  boolean existsByUserIdAndSkillId(UUID userId, UUID skillId);

  long countBySkillId(UUID skillId);

  long countByUserId(UUID userId);

  void deleteByUserIdAndId(UUID userId, UUID id);

  void deleteByUserId(UUID userId);
}
