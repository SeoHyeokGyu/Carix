package com.carix.skill.service;

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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SkillService {

  // TODO: [직군 카테고리 체계 확장]
  // 현재는 IT 개발 직군 위주 카테고리만 허용되어 있습니다.
  // 추후 DESIGN, MARKETING, FINANCE, SALES 등 전사 직군 코드체계(JobFamily) 연동 시 동적 검증 또는 공통 Enum으로 통합 필요
  private static final Set<String> ALLOWED_CATEGORIES =
      Set.of("BACKEND", "FRONTEND", "DEVOPS", "DATA", "AI_ML", "ETC");

  private final SkillRepository skillRepository;
  private final SkillAliasRepository skillAliasRepository;
  private final UserSkillRepository userSkillRepository;

  /** 표준 스킬 사전 검색 (별칭 포함 자동완성용) */
  public List<SkillSearchResponse> searchSkills(String query) {
    if (query == null || query.trim().isBlank()) {
      return List.of();
    }
    List<Skill> skills = skillRepository.searchStandardSkills(query.trim());
    return skills.stream()
        .map(s -> new SkillSearchResponse(s.getId(), s.getStandardName(), s.isCustom()))
        .toList();
  }

  /** 로그인 사용자의 보유 스킬 목록 조회 */
  public List<UserSkillResponse> getUserSkills(UUID userId) {
    return userSkillRepository.findByUserId(userId).stream()
        .map(this::mapToUserSkillResponse)
        .toList();
  }

  /** 사용자 보유 스킬 등록 */
  @Transactional
  public UserSkillResponse createUserSkill(UUID userId, UserSkillCreateRequest request) {
    String trimmedName = request.standardName().trim();

    // 1. 표준 스킬 또는 별칭으로 기존 표준 스킬 탐색
    Skill targetSkill = resolveOrCreateSkill(userId, trimmedName, request.category());

    // 2. 이미 동일 스킬이 등록되어 있는지 중복 검증
    if (userSkillRepository.existsByUserIdAndSkillId(userId, targetSkill.getId())) {
      throw new DuplicateSkillException("이미 등록된 스킬입니다: " + targetSkill.getStandardName());
    }

    // 3. UserSkill 생성 및 저장
    // TODO: [스킬 숙련도 평가 연동]
    // 추후 온보딩/프로필 고도화 시 숙련도(proficiency_level: BEGINNER, INTERMEDIATE, ADVANCED) 입력값 DTO 및 엔티티 매핑 추가
    UserSkill userSkill =
        UserSkill.builder()
            .userId(userId)
            .skill(targetSkill)
            .monthsOfExperience(request.monthsOfExperience())
            .lastUsedDate(request.lastUsedDate())
            .build();
    UserSkill saved = userSkillRepository.save(userSkill);

    return mapToUserSkillResponse(saved);
  }

  /** 사용자 보유 스킬 수정 */
  @Transactional
  public UserSkillResponse updateUserSkill(
      UUID userId, UUID userSkillId, UserSkillUpdateRequest request) {
    UserSkill userSkill =
        userSkillRepository
            .findByIdAndUserId(userSkillId, userId)
            .orElseThrow(() -> new UserSkillNotFoundException("수정할 보유 스킬을 찾을 수 없습니다."));

    userSkill.update(request.monthsOfExperience(), request.lastUsedDate());
    return mapToUserSkillResponse(userSkill);
  }

  /** 사용자 보유 스킬 삭제 (커스텀 스킬일 경우 미참조 시 자동 정리) */
  @Transactional
  public void deleteUserSkill(UUID userId, UUID userSkillId) {
    UserSkill userSkill =
        userSkillRepository
            .findByIdAndUserId(userSkillId, userId)
            .orElseThrow(() -> new UserSkillNotFoundException("삭제할 보유 스킬을 찾을 수 없습니다."));

    Skill skill = userSkill.getSkill();
    userSkillRepository.delete(userSkill);
    userSkillRepository.flush();

    // 커스텀 스킬이고 다른 사용자의 참조가 없으면 스킬 엔티티도 삭제
    if (skill.isCustom()) {
      long remainingReferences = userSkillRepository.countBySkillId(skill.getId());
      if (remainingReferences == 0) {
        skillRepository.delete(skill);
      }
    }
  }

  /**
   * 스킬 식별 또는 생성
   *
   * <p>TODO: [커스텀 스킬 승격(Promotion) 거버넌스] 특정 커스텀 스킬이 여러 사용자에게 등록되거나 채용공고 수집기에 빈번하게 등장할 경우, 관리자 검토를
   * 거쳐 표준 스킬(is_custom = false)로 승격하고 동의어/별칭(Alias)을 매핑하는 배치 파이프라인 연동 고려
   */
  private Skill resolveOrCreateSkill(UUID userId, String name, String category) {
    // 1. 표준 스킬명 일치 확인
    Optional<Skill> standardSkillOpt =
        skillRepository.findByStandardNameIgnoreCaseAndIsCustomFalse(name);
    if (standardSkillOpt.isPresent()) {
      return standardSkillOpt.get();
    }

    // 2. 표준 스킬 별칭 일치 확인
    Optional<SkillAlias> aliasOpt = skillAliasRepository.findByAliasNameIgnoreCase(name);
    if (aliasOpt.isPresent() && !aliasOpt.get().getSkill().isCustom()) {
      return aliasOpt.get().getSkill();
    }

    // 3. 사전에 없는 경우 -> 커스텀 스킬 생성
    validateCategory(category);
    String normalizedCategory = category.toUpperCase().trim();

    return skillRepository
        .findByStandardNameIgnoreCaseAndCategory(name, normalizedCategory)
        .orElseGet(
            () -> skillRepository.save(Skill.createCustom(name, normalizedCategory, userId)));
  }

  private void validateCategory(String category) {
    if (category == null || category.trim().isBlank()) {
      throw new InvalidSkillCategoryException(
          "표준 사전에 없는 스킬은 카테고리(BACKEND, FRONTEND, DEVOPS, DATA, AI_ML, ETC) 지정이 필수입니다.");
    }
    String upper = category.toUpperCase().trim();
    if (!ALLOWED_CATEGORIES.contains(upper)) {
      throw new InvalidSkillCategoryException(
          "지원하지 않는 카테고리입니다: " + category + ". 허용 카테고리: " + ALLOWED_CATEGORIES);
    }
  }

  private UserSkillResponse mapToUserSkillResponse(UserSkill userSkill) {
    Skill s = userSkill.getSkill();
    return new UserSkillResponse(
        userSkill.getId(),
        s.getId(),
        s.getStandardName(),
        s.isCustom(),
        userSkill.getMonthsOfExperience(),
        userSkill.getLastUsedDate());
  }
}
