package com.carix.skill.service;

import com.carix.skill.domain.Skill;
import com.carix.skill.domain.SkillAlias;
import com.carix.skill.domain.UserSkill;
import com.carix.skill.dto.SkillRecommendationsResponse;
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

  /** 동료집단 기반 추천 스킬 조회 */
  public SkillRecommendationsResponse getSkillRecommendations(UUID userId) {
    return new SkillRecommendationsResponse(
        new SkillRecommendationsResponse.PeerGroupDetail("서울 전체 / 경력 MID / 회사규모 무관"),
        List.of(
            new SkillRecommendationsResponse.RecommendedSkillDetail(
                "Kubernetes", "DEVOPS", 0.245, "UP", "동료집단 내 공고의 24.5%에서 요구하고 있으며, 최근 수요가 급증세입니다."),
            new SkillRecommendationsResponse.RecommendedSkillDetail(
                "TypeScript", "FRONTEND", 0.182, "STABLE", "동료집단 내 공고의 18.2%에서 요구되는 핵심 스택입니다.")));
  }

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
