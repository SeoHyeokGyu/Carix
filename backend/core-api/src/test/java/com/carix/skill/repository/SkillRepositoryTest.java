package com.carix.skill.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.skill.domain.Skill;
import com.carix.skill.domain.SkillAlias;
import com.carix.skill.domain.UserSkill;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SkillRepositoryTest {

  @Autowired private SkillRepository skillRepository;
  @Autowired private SkillAliasRepository skillAliasRepository;
  @Autowired private UserSkillRepository userSkillRepository;

  @Test
  @DisplayName("searchStandardSkills - 표준 스킬명 또는 별칭에 검색어가 포함된 표준 스킬을 중복 없이 검색한다")
  void searchStandardSkills_searchesByStandardNameAndAlias() {
    // Given
    Skill standardSkill = skillRepository.save(Skill.createStandard("Spring Boot", "BACKEND"));
    Skill customSkill =
        skillRepository.save(Skill.createCustom("Spring Custom", "BACKEND", UUID.randomUUID()));

    skillAliasRepository.save(new SkillAlias(standardSkill, "springboot"));
    skillAliasRepository.save(new SkillAlias(standardSkill, "스프링부트"));

    // When - 표준 스킬명 검색
    List<Skill> resultByName = skillRepository.searchStandardSkills("spring");

    // When - 별칭 검색
    List<Skill> resultByAlias = skillRepository.searchStandardSkills("스프링");

    // Then
    assertThat(resultByName).extracting(Skill::getStandardName).contains("Spring Boot");
    assertThat(resultByName).extracting(Skill::isCustom).doesNotContain(true); // 커스텀 스킬은 제외

    assertThat(resultByAlias).hasSize(1);
    assertThat(resultByAlias.get(0).getStandardName()).isEqualTo("Spring Boot");
  }

  @Test
  @DisplayName("findByUserId - 사용자가 등록한 보유 스킬 목록을 Skill과 함께 조회한다")
  void findByUserId_fetchesUserSkills() {
    // Given
    UUID userId = UUID.randomUUID();
    Skill skill1 = skillRepository.save(Skill.createStandard("Java", "BACKEND"));
    Skill skill2 = skillRepository.save(Skill.createStandard("React", "FRONTEND"));

    userSkillRepository.save(
        UserSkill.builder()
            .userId(userId)
            .skill(skill1)
            .monthsOfExperience(24)
            .lastUsedDate(LocalDate.of(2026, 7, 1))
            .build());
    userSkillRepository.save(
        UserSkill.builder()
            .userId(userId)
            .skill(skill2)
            .monthsOfExperience(12)
            .lastUsedDate(LocalDate.of(2026, 6, 1))
            .build());

    // When
    List<UserSkill> userSkills = userSkillRepository.findByUserId(userId);

    // Then
    assertThat(userSkills).hasSize(2);
    assertThat(userSkills)
        .extracting(us -> us.getSkill().getStandardName())
        .containsExactlyInAnyOrder("Java", "React");
  }

  @Test
  @DisplayName("countBySkillId - 특정 스킬을 참조하고 있는 UserSkill 레코드 수를 반환한다")
  void countBySkillId_returnsReferenceCount() {
    // Given
    Skill skill = skillRepository.save(Skill.createStandard("Docker", "DEVOPS"));
    UUID user1 = UUID.randomUUID();
    UUID user2 = UUID.randomUUID();

    userSkillRepository.save(
        UserSkill.builder()
            .userId(user1)
            .skill(skill)
            .monthsOfExperience(10)
            .lastUsedDate(LocalDate.of(2026, 1, 1))
            .build());
    userSkillRepository.save(
        UserSkill.builder()
            .userId(user2)
            .skill(skill)
            .monthsOfExperience(20)
            .lastUsedDate(LocalDate.of(2026, 2, 1))
            .build());

    // When
    long count = userSkillRepository.countBySkillId(skill.getId());

    // Then
    assertThat(count).isEqualTo(2L);
  }

  @Test
  @DisplayName("findByIdAndUserId - 본인 소유의 UserSkill만 조회할 수 있다")
  void findByIdAndUserId_onlyOwnerCanFind() {
    // Given
    UUID ownerId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    Skill skill = skillRepository.save(Skill.createStandard("Kubernetes", "DEVOPS"));

    UserSkill saved =
        userSkillRepository.save(
            UserSkill.builder()
                .userId(ownerId)
                .skill(skill)
                .monthsOfExperience(15)
                .lastUsedDate(LocalDate.of(2026, 5, 1))
                .build());

    // When & Then
    Optional<UserSkill> ownerFind = userSkillRepository.findByIdAndUserId(saved.getId(), ownerId);
    Optional<UserSkill> otherFind =
        userSkillRepository.findByIdAndUserId(saved.getId(), otherUserId);

    assertThat(ownerFind).isPresent();
    assertThat(otherFind).isEmpty();
  }
}
