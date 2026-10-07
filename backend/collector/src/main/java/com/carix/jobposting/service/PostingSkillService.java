package com.carix.jobposting.service;

import com.carix.jobposting.CollectedVia;
import com.carix.jobposting.JobPosting;
import com.carix.jobposting.JobPostingRepository;
import com.carix.jobposting.JobPostingSkillRepository;
import com.carix.jobposting.Skill;
import com.carix.jobposting.SkillAliasRepository;
import com.carix.jobposting.SkillRepository;
import com.carix.jobposting.UserSkillRepository;
import com.carix.jobposting.normalize.SkillTaxonomy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공고 스킬의 정규화·저장·재정제·중복 병합을 맡는다. 수집 서비스들이 공고에 스킬을 붙일 때 공유한다. */
@Slf4j
@Service
public class PostingSkillService {

  private final JobPostingRepository jobPostingRepository;
  private final SkillRepository skillRepository;
  private final JobPostingSkillRepository jobPostingSkillRepository;
  private final SkillAliasRepository skillAliasRepository;
  private final UserSkillRepository userSkillRepository;
  private final SkillTaxonomy skillTaxonomy;

  @Autowired
  public PostingSkillService(
      JobPostingRepository jobPostingRepository,
      SkillRepository skillRepository,
      @Autowired(required = false) JobPostingSkillRepository jobPostingSkillRepository,
      @Autowired(required = false) SkillAliasRepository skillAliasRepository,
      @Autowired(required = false) UserSkillRepository userSkillRepository,
      SkillTaxonomy skillTaxonomy) {
    this.jobPostingRepository = jobPostingRepository;
    this.skillRepository = skillRepository;
    this.jobPostingSkillRepository = jobPostingSkillRepository;
    this.skillAliasRepository = skillAliasRepository;
    this.userSkillRepository = userSkillRepository;
    this.skillTaxonomy = skillTaxonomy;
  }

  /** 공고 제목과 원천 스킬 태그 목록을 결합하여 표준 스킬을 추출하고 스킬 행을 준비합니다. 불필요한 1글자 잡음이나 노이즈는 제외됩니다. */
  public List<Skill> resolveSkills(String title, List<String> rawSkills) {
    Set<String> combinedCanonicalSkills = new LinkedHashSet<>();

    // 1. 공고 제목(자연어)에서 단어 경계를 준수하여 사전 기반 스킬 추출
    if (title != null && !title.isBlank()) {
      combinedCanonicalSkills.addAll(skillTaxonomy.extractSkillsFromSentence(title));
    }

    // 2. 크롤링된 태그/상세 원천 스킬 목록에서 추출
    if (rawSkills != null) {
      for (String rawName : rawSkills) {
        combinedCanonicalSkills.addAll(skillTaxonomy.extractCanonicalSkills(rawName));
      }
    }

    List<Skill> targetSkills = new ArrayList<>();
    Set<String> appliedKeys = new LinkedHashSet<>();
    for (String canonicalName : combinedCanonicalSkills) {
      if (canonicalName == null || canonicalName.isBlank()) {
        continue;
      }
      String category = skillTaxonomy.category(canonicalName);
      if (appliedKeys.add(canonicalName + " " + category)) {
        targetSkills.add(upsertSkill(canonicalName, category));
      }
    }

    return targetSkills;
  }

  /** 사전에 있는 표준 스킬 이름으로 스킬 행을 찾거나 만든다. */
  public Skill upsertKnownSkill(String canonicalName) {
    return upsertSkill(canonicalName, skillTaxonomy.category(canonicalName));
  }

  /** 공고 스킬을 원천 재파싱 결과에 맞춘다. 점핏으로 보강된 공고는 점핏 기술 스택이 사람인 목록에 없으므로 더하기만 한다 — 바꾸면 점핏 스킬이 지워진다. */
  public void applySkillsToPosting(JobPosting posting, String title, List<String> rawSkills) {
    applySkillsToPosting(posting, title, rawSkills, List.of());
  }

  /**
   * 제목·태그에서 뽑은 스킬에 {@code extraSkills} 를 합쳐 맞춘다. 산업 태그처럼 제목·태그 추출로는 다시 나오지 않는 스킬을 지켜야 할 때 쓴다 — 바꾼
   * 뒤에 따로 다시 붙이면 같은 플러시에서 삭제 대신 삽입이 먼저 나가 유니크 제약에 걸린다.
   */
  public void applySkillsToPosting(
      JobPosting posting, String title, List<String> rawSkills, List<Skill> extraSkills) {
    Map<UUID, Skill> merged = new LinkedHashMap<>();
    for (Skill skill : resolveSkills(title, rawSkills)) {
      merged.putIfAbsent(skill.getId(), skill);
    }
    for (Skill skill : extraSkills) {
      merged.putIfAbsent(skill.getId(), skill);
    }
    List<Skill> targetSkills = List.copyOf(merged.values());
    if (posting.hasCollectedVia(CollectedVia.JUMPIT)) {
      posting.addSkillsIfAbsent(targetSkills);
    } else {
      posting.replaceSkills(targetSkills);
    }
  }

  /**
   * DB에 적재된 모든 채용공고의 스킬을 공고 제목과 기존 태그를 바탕으로 일괄 재정제(Refinement)합니다. 크롤링 완료 후 스킬이 누락되었거나 노이즈가 섞인 공고들을
   * 깨끗하게 복구합니다.
   */
  @Transactional
  public Map<String, Object> refineAllPostingSkills() {
    // 0. 기존 DB에 누적된 중복 standard_name 스킬 병합 정리
    int deduped = deduplicateSkills();

    // 1. 최신 스킬 사전 캐시 갱신
    skillTaxonomy.reloadTaxonomy();

    List<JobPosting> postings = jobPostingRepository.findAll();
    int total = postings.size();
    int refinedCount = 0;
    int withSkillsCount = 0;
    int totalSkillLinks = 0;

    log.info("[스킬 재정제] 총 {}건의 공고를 대상으로 재정제를 시작합니다. (중복 정리 {}건)", total, deduped);

    for (JobPosting posting : postings) {
      List<String> currentSkillNames =
          posting.getSkills().stream().map(Skill::getStandardName).toList();

      List<Skill> industrySkills =
          posting.getSkills().stream().filter(s -> "INDUSTRY".equals(s.getCategory())).toList();
      applySkillsToPosting(posting, posting.getTitle(), currentSkillNames, industrySkills);
      jobPostingRepository.save(posting);

      int count = posting.getSkills().size();
      if (count > 0) {
        withSkillsCount++;
        totalSkillLinks += count;
      }
      refinedCount++;
    }

    log.info(
        "[스킬 재정제] 완료: 총 {}건 대상 중 스킬 보유 공고 {}건, 총 매핑 링크 {}건",
        refinedCount,
        withSkillsCount,
        totalSkillLinks);

    return Map.of(
        "totalPostings", total,
        "refinedPostings", refinedCount,
        "postingsWithSkills", withSkillsCount,
        "totalSkillLinks", totalSkillLinks,
        "dedupedSkills", deduped);
  }

  /**
   * DB에 동일한 standard_name(대소문자 무시)으로 여러 행이 존재하는 경우, 가장 구체적인 카테고리를 가진 행(SKILL 이외)을 대표로 남기고 나머지를 벌크
   * 쿼리로 안전하게 병합·정리합니다. (영속성 컨텍스트 충돌 및 외래키 제약 오류 방지)
   */
  @Transactional
  public int deduplicateSkills() {
    List<Skill> allSkills = skillRepository.findAll();
    Map<String, List<Skill>> byName = new HashMap<>();
    for (Skill s : allSkills) {
      byName
          .computeIfAbsent(
              s.getStandardName().strip().toLowerCase(java.util.Locale.ROOT),
              k -> new ArrayList<>())
          .add(s);
    }

    int removedDuplicates = 0;
    for (List<Skill> dupGroup : byName.values()) {
      if (dupGroup.size() <= 1) {
        continue;
      }

      // 1. 대표 스킬 선택: 카테고리가 "SKILL"이 아닌 것 우선, 없으면 첫 번째
      Skill canonical =
          dupGroup.stream()
              .filter(s -> !"SKILL".equalsIgnoreCase(s.getCategory()))
              .findFirst()
              .orElse(dupGroup.get(0));

      for (Skill dup : dupGroup) {
        if (dup.getId().equals(canonical.getId())) {
          continue;
        }

        UUID dupId = dup.getId();
        UUID canonicalId = canonical.getId();

        // 2. JobPostingSkill 링크 정리 (벌크 쿼리 지원 시 안전하게 선행 삭제 후 재할당)
        if (jobPostingSkillRepository != null) {
          jobPostingSkillRepository.deleteDuplicateLinks(dupId, canonicalId);
          jobPostingSkillRepository.reassignSkill(dupId, canonical);
        } else {
          for (JobPosting posting : jobPostingRepository.findAll()) {
            boolean hasDup = posting.getSkills().stream().anyMatch(s -> s.getId().equals(dupId));
            if (hasDup) {
              List<Skill> updatedSkills = new ArrayList<>(posting.getSkills());
              updatedSkills.removeIf(s -> s.getId().equals(dupId));
              if (updatedSkills.stream().noneMatch(s -> s.getId().equals(canonicalId))) {
                updatedSkills.add(canonical);
              }
              posting.replaceSkills(updatedSkills);
              jobPostingRepository.save(posting);
            }
          }
        }

        // 3. SkillAlias 별칭 링크 정리
        if (skillAliasRepository != null) {
          try {
            skillAliasRepository.reassignSkill(dupId, canonical);
          } catch (Exception e) {
            log.trace("[스킬 중복제거] 별칭 정리 건너뜀: {}", e.getMessage());
          }
        }

        // 4. UserSkill (사용자 보유 스킬) 링크 정리
        if (userSkillRepository != null) {
          try {
            userSkillRepository.deleteDuplicateLinks(dupId, canonicalId);
            userSkillRepository.reassignSkill(dupId, canonical);
          } catch (Exception e) {
            log.trace("[스킬 중복제거] user_skill 정리 건너뜀: {}", e.getMessage());
          }
        }

        // 5. 미참조 상태가 된 중복 스킬 엔티티 직접 삭제
        try {
          skillRepository.deleteSkillDirectly(dupId);
          removedDuplicates++;
        } catch (Exception e) {
          log.warn("[스킬 중복제거] 스킬 ID {} 삭제 중 건너뜀: {}", dupId, e.getMessage());
        }
      }
    }

    if (removedDuplicates > 0) {
      log.info("[스킬 중복제거] 총 {}건의 중복 스킬 엔티티를 병합 및 삭제했습니다.", removedDuplicates);
    }
    return removedDuplicates;
  }

  public Skill upsertSkill(String standardName, String category) {
    Optional<Skill> byName = skillRepository.findFirstByStandardNameIgnoreCase(standardName);
    if (byName.isPresent()) {
      Skill existing = byName.get();
      if ("SKILL".equalsIgnoreCase(existing.getCategory())
          && category != null
          && !"SKILL".equalsIgnoreCase(category)) {
        existing.recategorize(category);
        return skillRepository.save(existing);
      }
      return existing;
    }
    return skillRepository
        .findByStandardNameAndCategory(standardName, category)
        .orElseGet(() -> skillRepository.save(new Skill(standardName, category, false)));
  }
}
