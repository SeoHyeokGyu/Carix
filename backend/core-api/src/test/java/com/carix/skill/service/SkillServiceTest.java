package com.carix.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.carix.skill.domain.Skill;
import com.carix.skill.domain.SkillAlias;
import com.carix.skill.domain.UserSkill;
import com.carix.skill.dto.SkillSearchResponse;
import com.carix.skill.dto.UserSkillCreateRequest;
import com.carix.skill.dto.UserSkillResponse;
import com.carix.skill.dto.UserSkillUpdateRequest;
import com.carix.skill.exception.DuplicateSkillException;
import com.carix.skill.exception.InvalidSkillCategoryException;
import com.carix.skill.exception.UserSkillNotFoundException;
import com.carix.skill.repository.SkillAliasRepository;
import com.carix.skill.repository.SkillRepository;
import com.carix.skill.repository.UserSkillRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

  @Mock private SkillRepository skillRepository;
  @Mock private SkillAliasRepository skillAliasRepository;
  @Mock private UserSkillRepository userSkillRepository;

  @InjectMocks private SkillService skillService;

  @Test
  @DisplayName("스킬 사전 검색 - 검색어가 포함된 표준 스킬 목록을 반환한다")
  void searchSkills_success() {
    Skill skill = Skill.createStandard("Spring Boot", "BACKEND");
    given(skillRepository.searchStandardSkills("spring")).willReturn(List.of(skill));

    List<SkillSearchResponse> result = skillService.searchSkills("spring");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).standardName()).isEqualTo("Spring Boot");
    assertThat(result.get(0).isCustom()).isFalse();
  }

  @Test
  @DisplayName("스킬 사전 검색 - 검색어가 공백이면 빈 목록을 반환한다")
  void searchSkills_emptyQuery() {
    List<SkillSearchResponse> result = skillService.searchSkills("   ");

    assertThat(result).isEmpty();
    verifyNoInteractions(skillRepository);
  }

  @Test
  @DisplayName("사용자 스킬 등록 - 표준 스킬 사전에서 찾아 정상 등록한다")
  void createUserSkill_withStandardSkill_success() {
    UUID userId = UUID.randomUUID();
    Skill skill = Skill.createStandard("Spring Boot", "BACKEND");
    UserSkillCreateRequest request =
        new UserSkillCreateRequest("Spring Boot", 24, LocalDate.of(2026, 7, 1), null);

    given(skillRepository.findByStandardNameIgnoreCaseAndIsCustomFalse("Spring Boot"))
        .willReturn(Optional.of(skill));
    given(userSkillRepository.existsByUserIdAndSkillId(userId, skill.getId())).willReturn(false);
    given(userSkillRepository.save(any(UserSkill.class)))
        .willAnswer(
            invocation -> {
              UserSkill us = invocation.getArgument(0);
              return UserSkill.builder()
                  .id(UUID.randomUUID())
                  .userId(us.getUserId())
                  .skill(us.getSkill())
                  .monthsOfExperience(us.getMonthsOfExperience())
                  .lastUsedDate(us.getLastUsedDate())
                  .build();
            });

    UserSkillResponse response = skillService.createUserSkill(userId, request);

    assertThat(response.standardName()).isEqualTo("Spring Boot");
    assertThat(response.isCustom()).isFalse();
    assertThat(response.monthsOfExperience()).isEqualTo(24);
  }

  @Test
  @DisplayName("사용자 스킬 등록 - 별칭(Alias)으로 표준 스킬을 찾아 정상 등록한다")
  void createUserSkill_withAlias_success() {
    UUID userId = UUID.randomUUID();
    Skill standardSkill = Skill.createStandard("Spring Boot", "BACKEND");
    SkillAlias alias = new SkillAlias(standardSkill, "springboot");
    UserSkillCreateRequest request =
        new UserSkillCreateRequest("springboot", 12, LocalDate.of(2026, 6, 1), null);

    given(skillRepository.findByStandardNameIgnoreCaseAndIsCustomFalse("springboot"))
        .willReturn(Optional.empty());
    given(skillAliasRepository.findByAliasNameIgnoreCase("springboot"))
        .willReturn(Optional.of(alias));
    given(userSkillRepository.existsByUserIdAndSkillId(userId, standardSkill.getId()))
        .willReturn(false);
    given(userSkillRepository.save(any(UserSkill.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    UserSkillResponse response = skillService.createUserSkill(userId, request);

    assertThat(response.standardName()).isEqualTo("Spring Boot");
    assertThat(response.isCustom()).isFalse();
  }

  @Test
  @DisplayName("사용자 스킬 등록 - 사전에 없는 스킬은 커스텀 스킬로 생성된다")
  void createUserSkill_customSkill_success() {
    UUID userId = UUID.randomUUID();
    UserSkillCreateRequest request =
        new UserSkillCreateRequest("MyCustomLib", 6, LocalDate.of(2026, 5, 1), "BACKEND");

    given(skillRepository.findByStandardNameIgnoreCaseAndIsCustomFalse("MyCustomLib"))
        .willReturn(Optional.empty());
    given(skillAliasRepository.findByAliasNameIgnoreCase("MyCustomLib"))
        .willReturn(Optional.empty());
    given(skillRepository.findByStandardNameIgnoreCaseAndCategory("MyCustomLib", "BACKEND"))
        .willReturn(Optional.empty());

    Skill customSkill = Skill.createCustom("MyCustomLib", "BACKEND", userId);
    given(skillRepository.save(any(Skill.class))).willReturn(customSkill);
    given(userSkillRepository.existsByUserIdAndSkillId(userId, customSkill.getId()))
        .willReturn(false);
    given(userSkillRepository.save(any(UserSkill.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    UserSkillResponse response = skillService.createUserSkill(userId, request);

    assertThat(response.standardName()).isEqualTo("MyCustomLib");
    assertThat(response.isCustom()).isTrue();
  }

  @Test
  @DisplayName("사용자 스킬 등록 - 사전에 없는 스킬인데 카테고리가 없으면 예외 발생")
  void createUserSkill_customSkill_missingCategory_throwsException() {
    UUID userId = UUID.randomUUID();
    UserSkillCreateRequest request =
        new UserSkillCreateRequest("UnknownLib", 6, LocalDate.of(2026, 5, 1), null);

    given(skillRepository.findByStandardNameIgnoreCaseAndIsCustomFalse("UnknownLib"))
        .willReturn(Optional.empty());
    given(skillAliasRepository.findByAliasNameIgnoreCase("UnknownLib"))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> skillService.createUserSkill(userId, request))
        .isInstanceOf(InvalidSkillCategoryException.class);
  }

  @Test
  @DisplayName("사용자 스킬 등록 - 이미 등록된 스킬이면 DuplicateSkillException 발생")
  void createUserSkill_duplicate_throwsException() {
    UUID userId = UUID.randomUUID();
    Skill skill = Skill.createStandard("Java", "BACKEND");
    UserSkillCreateRequest request =
        new UserSkillCreateRequest("Java", 36, LocalDate.of(2026, 7, 1), null);

    given(skillRepository.findByStandardNameIgnoreCaseAndIsCustomFalse("Java"))
        .willReturn(Optional.of(skill));
    given(userSkillRepository.existsByUserIdAndSkillId(userId, skill.getId())).willReturn(true);

    assertThatThrownBy(() -> skillService.createUserSkill(userId, request))
        .isInstanceOf(DuplicateSkillException.class)
        .hasMessageContaining("이미 등록된 스킬입니다");
  }

  @Test
  @DisplayName("사용자 스킬 수정 - 개월수 및 마지막 사용일을 수정한다")
  void updateUserSkill_success() {
    UUID userId = UUID.randomUUID();
    UUID userSkillId = UUID.randomUUID();
    Skill skill = Skill.createStandard("Spring Boot", "BACKEND");
    UserSkill userSkill =
        UserSkill.builder()
            .id(userSkillId)
            .userId(userId)
            .skill(skill)
            .monthsOfExperience(12)
            .lastUsedDate(LocalDate.of(2025, 1, 1))
            .build();

    UserSkillUpdateRequest request = new UserSkillUpdateRequest(36, LocalDate.of(2026, 8, 1));

    given(userSkillRepository.findByIdAndUserId(userSkillId, userId))
        .willReturn(Optional.of(userSkill));

    UserSkillResponse response = skillService.updateUserSkill(userId, userSkillId, request);

    assertThat(response.monthsOfExperience()).isEqualTo(36);
    assertThat(response.lastUsedDate()).isEqualTo(LocalDate.of(2026, 8, 1));
  }

  @Test
  @DisplayName("사용자 스킬 수정 - 존재하지 않는 스킬이면 UserSkillNotFoundException 발생")
  void updateUserSkill_notFound_throwsException() {
    UUID userId = UUID.randomUUID();
    UUID userSkillId = UUID.randomUUID();
    UserSkillUpdateRequest request = new UserSkillUpdateRequest(36, LocalDate.of(2026, 8, 1));

    given(userSkillRepository.findByIdAndUserId(userSkillId, userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> skillService.updateUserSkill(userId, userSkillId, request))
        .isInstanceOf(UserSkillNotFoundException.class);
  }

  @Test
  @DisplayName("사용자 스킬 삭제 - 커스텀 스킬의 남은 참조자가 없으면 커스텀 스킬 엔티티도 삭제된다")
  void deleteUserSkill_customSkill_orphanCleanup_success() {
    UUID userId = UUID.randomUUID();
    UUID userSkillId = UUID.randomUUID();
    Skill customSkill = Skill.createCustom("CustomTool", "DEVOPS", userId);
    UserSkill userSkill =
        UserSkill.builder()
            .id(userSkillId)
            .userId(userId)
            .skill(customSkill)
            .monthsOfExperience(6)
            .lastUsedDate(LocalDate.of(2026, 1, 1))
            .build();

    given(userSkillRepository.findByIdAndUserId(userSkillId, userId))
        .willReturn(Optional.of(userSkill));
    given(userSkillRepository.countBySkillId(customSkill.getId())).willReturn(0L);

    skillService.deleteUserSkill(userId, userSkillId);

    verify(userSkillRepository).delete(userSkill);
    verify(skillRepository).delete(customSkill);
  }

  @Test
  @DisplayName("사용자 스킬 삭제 - 표준 스킬은 UserSkill만 삭제되고 Skill은 유지된다")
  void deleteUserSkill_standardSkill_maintainsSkill() {
    UUID userId = UUID.randomUUID();
    UUID userSkillId = UUID.randomUUID();
    Skill standardSkill = Skill.createStandard("Docker", "DEVOPS");
    UserSkill userSkill =
        UserSkill.builder()
            .id(userSkillId)
            .userId(userId)
            .skill(standardSkill)
            .monthsOfExperience(12)
            .lastUsedDate(LocalDate.of(2026, 1, 1))
            .build();

    given(userSkillRepository.findByIdAndUserId(userSkillId, userId))
        .willReturn(Optional.of(userSkill));

    skillService.deleteUserSkill(userId, userSkillId);

    verify(userSkillRepository).delete(userSkill);
    verify(skillRepository, never()).delete(any(Skill.class));
  }

  @Test
  @DisplayName("사용자 스킬 등록 - 지원하지 않는 카테고리명인 경우 InvalidSkillCategoryException 발생")
  void createUserSkill_customSkill_unsupportedCategory_throwsException() {
    UUID userId = UUID.randomUUID();
    UserSkillCreateRequest request =
        new UserSkillCreateRequest("NewTool", 6, LocalDate.of(2026, 5, 1), "UNSUPPORTED_CATEGORY");

    given(skillRepository.findByStandardNameIgnoreCaseAndIsCustomFalse("NewTool"))
        .willReturn(Optional.empty());
    given(skillAliasRepository.findByAliasNameIgnoreCase("NewTool")).willReturn(Optional.empty());

    assertThatThrownBy(() -> skillService.createUserSkill(userId, request))
        .isInstanceOf(InvalidSkillCategoryException.class)
        .hasMessageContaining("지원하지 않는 카테고리입니다");
  }

  @Test
  @DisplayName("사용자 스킬 삭제 - 커스텀 스킬이라도 다른 사용자의 참조가 남아있으면 Skill 엔티티를 삭제하지 않는다")
  void deleteUserSkill_customSkill_hasRemainingReferences_doesNotDeleteSkill() {
    UUID userId = UUID.randomUUID();
    UUID userSkillId = UUID.randomUUID();
    Skill customSkill = Skill.createCustom("CustomTool", "DEVOPS", userId);
    UserSkill userSkill =
        UserSkill.builder()
            .id(userSkillId)
            .userId(userId)
            .skill(customSkill)
            .monthsOfExperience(6)
            .lastUsedDate(LocalDate.of(2026, 1, 1))
            .build();

    given(userSkillRepository.findByIdAndUserId(userSkillId, userId))
        .willReturn(Optional.of(userSkill));
    given(userSkillRepository.countBySkillId(customSkill.getId())).willReturn(1L);

    skillService.deleteUserSkill(userId, userSkillId);

    verify(userSkillRepository).delete(userSkill);
    verify(skillRepository, never()).delete(any(Skill.class));
  }

  @Test
  @DisplayName("사용자 스킬 등록 - 이미 30개의 스킬이 등록된 경우 MaxSkillLimitExceededException 발생")
  void createUserSkill_exceedsMaxSkillLimit_throwsException() {
    UUID userId = UUID.randomUUID();
    UserSkillCreateRequest request =
        new UserSkillCreateRequest("Kubernetes", 12, LocalDate.of(2026, 1, 1), "DEVOPS");

    given(userSkillRepository.countByUserId(userId))
        .willReturn((long) SkillService.MAX_USER_SKILLS);

    assertThatThrownBy(() -> skillService.createUserSkill(userId, request))
        .isInstanceOf(com.carix.skill.exception.MaxSkillLimitExceededException.class)
        .hasMessageContaining("보유 스킬은 최대 30개까지만 등록할 수 있습니다.");

    verify(userSkillRepository, never()).save(any(UserSkill.class));
  }

  @Test
  @DisplayName("사용자 스킬 등록 - 미래 일자가 마지막 사용일로 전달되면 IllegalArgumentException 발생")
  void createUserSkill_futureDate_throwsException() {
    UUID userId = UUID.randomUUID();
    LocalDate futureDate = LocalDate.now().plusDays(10);
    UserSkillCreateRequest request =
        new UserSkillCreateRequest("Kubernetes", 12, futureDate, "DEVOPS");

    assertThatThrownBy(() -> skillService.createUserSkill(userId, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("마지막 사용일은 미래 날짜일 수 없습니다");

    verifyNoInteractions(skillRepository);
  }

  @Test
  @DisplayName("사용자 스킬 수정 - 미래 일자가 마지막 사용일로 전달되면 IllegalArgumentException 발생")
  void updateUserSkill_futureDate_throwsException() {
    UUID userId = UUID.randomUUID();
    UUID userSkillId = UUID.randomUUID();
    LocalDate futureDate = LocalDate.now().plusDays(5);
    UserSkillUpdateRequest request = new UserSkillUpdateRequest(24, futureDate);

    assertThatThrownBy(() -> skillService.updateUserSkill(userId, userSkillId, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("마지막 사용일은 미래 날짜일 수 없습니다");

    verifyNoInteractions(userSkillRepository);
  }
}
