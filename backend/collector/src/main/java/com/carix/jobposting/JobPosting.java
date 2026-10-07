package com.carix.jobposting;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "job_posting")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobPosting {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  @Column(nullable = false, length = 20)
  private String source;

  @Column(name = "external_id", nullable = false, length = 100)
  private String externalId;

  @Column(nullable = false, length = 500)
  private String title;

  @Column(name = "company_name")
  private String companyName;

  @Column(name = "salary_min")
  private Long salaryMin;

  @Column(name = "salary_max")
  private Long salaryMax;

  /** 회사가 검색용으로 넣은 비공개 구간(연간 원). 공개 연봉이 아니라 제시 연봉 통계에 쓰지 않는다. */
  @Column(name = "salary_band_min")
  private Long salaryBandMin;

  @Column(name = "salary_band_max")
  private Long salaryBandMax;

  /** {@link com.carix.jobposting.normalize.SalaryDisclosure} 이름. */
  @Column(name = "salary_disclosure", nullable = false, length = 16)
  @Builder.Default
  private String salaryDisclosure = "UNKNOWN";

  @Column private String region;

  @Column(name = "experience_min")
  private Integer experienceMin;

  @Column(name = "experience_max")
  private Integer experienceMax;

  @Column(name = "employment_type")
  private String employmentType;

  @Column(name = "company_size")
  private String companySize;

  @Column(name = "company_badge")
  private String companyBadge;

  @Column(name = "position_level")
  private String positionLevel;

  @Column(name = "job_family")
  private String jobFamily;

  @Column(name = "posted_start_date")
  private LocalDate postedStartDate;

  @Column(name = "posted_end_date")
  private LocalDate postedEndDate;

  @Column(name = "first_seen_month", length = 6)
  private String firstSeenMonth;

  @Column(name = "last_seen_month", length = 6)
  private String lastSeenMonth;

  /**
   * 무작위 ID 표본으로 뽑힌 달(게시일 기준). 잡코리아 시장 집계는 이 표본만 센다 — 정기 수집(목록 상단)과 표본은 모집단이 달라 섞으면 수요율이 수집 방식만으로
   * 움직인다. 표본이 아니면 {@code null}.
   */
  @Column(name = "sampled_month", length = 6)
  private String sampledMonth;

  @Column(name = "source_job_tags", columnDefinition = "text")
  private String sourceJobTags;

  @Column(name = "collected_via", length = 128)
  private String collectedVia;

  @Column(name = "collected_at", nullable = false)
  private Instant collectedAt;

  @Column(name = "dedup_group_id")
  @Builder.Default
  private UUID dedupGroupId = UUID.randomUUID();

  @Column(name = "dedup_rule_version", length = 20)
  @Builder.Default
  private String dedupRuleVersion = "v0-none";

  @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
  @Builder.Default
  private List<JobPostingSkill> jobPostingSkills = new ArrayList<>();

  public JobPosting(
      String source,
      String externalId,
      String title,
      String companyName,
      Long salaryMin,
      Long salaryMax,
      String region,
      Integer experienceMin,
      Integer experienceMax,
      String employmentType,
      String companySize,
      String companyBadge,
      String positionLevel,
      String jobFamily,
      LocalDate postedStartDate,
      LocalDate postedEndDate,
      String firstSeenMonth,
      String lastSeenMonth,
      Instant collectedAt) {
    this.id = UUID.randomUUID();
    this.source = source;
    this.externalId = externalId;
    this.title = title;
    this.companyName = companyName;
    this.salaryMin = normalizeSalary(salaryMin);
    this.salaryMax = normalizeSalary(salaryMax);
    this.region = region;
    this.experienceMin = experienceMin;
    this.experienceMax = experienceMax;
    this.employmentType = employmentType;
    this.companySize = companySize;
    this.companyBadge = companyBadge;
    this.positionLevel = positionLevel;
    this.jobFamily = jobFamily;
    this.postedStartDate = postedStartDate;
    this.postedEndDate = postedEndDate;
    this.firstSeenMonth = firstSeenMonth;
    this.lastSeenMonth = lastSeenMonth;
    this.collectedAt = collectedAt;
    this.salaryDisclosure = "UNKNOWN";
    this.dedupGroupId = UUID.randomUUID();
    this.dedupRuleVersion = "v0-none";
    this.jobPostingSkills = new ArrayList<>();
  }

  public void addSkill(Skill skill) {
    if (skill != null) {
      jobPostingSkills.add(new JobPostingSkill(this, skill));
    }
  }

  /** 급여 판정 결과를 반영한다. 공개 연봉과 비공개 구간은 함께 갖지 않는다. */
  public void applySalary(com.carix.jobposting.normalize.PostingSalary salary) {
    this.salaryMin = salary.min();
    this.salaryMax = salary.max();
    this.salaryBandMin = salary.bandMin();
    this.salaryBandMax = salary.bandMax();
    this.salaryDisclosure = salary.disclosure().name();
  }

  /** 재수집에 실패한 공고. 판정 전 값을 버리지 않고 비공개 구간 자리로 옮겨 둔다(설계 §5.2). */
  public void markSalaryUnreachable() {
    if (salaryMin != null || salaryMax != null) {
      this.salaryBandMin = salaryMin;
      this.salaryBandMax = salaryMax;
    }
    this.salaryMin = null;
    this.salaryMax = null;
    this.salaryDisclosure = "UNKNOWN";
  }

  private static Long normalizeSalary(Long value) {
    if (value == null) {
      return null;
    }
    if (value > 0 && value < 1_000_000) {
      return value * 10_000L;
    }
    return value;
  }

  /** 공고가 요구하는 경력 연차 범위. 하한이 동료집단 브래킷의 기준이다 — 공고가 받아 주는 가장 낮은 연차로 비교한다. */
  public void applyExperienceRange(Integer min, Integer max) {
    this.experienceMin = min;
    this.experienceMax = max;
  }

  /** 공고가 계속 활성 상태로 수집될 때 마지막 관측월(lastSeenMonth)을 갱신합니다. */
  public void updateLastSeenMonth(String currentMonth) {
    if (currentMonth != null && currentMonth.matches("\\d{6}")) {
      this.lastSeenMonth = currentMonth;
    }
  }

  /** 무작위 표본에 뽑혔음을 표시한다. 이미 정기 수집으로 들어온 공고도 표본에 뽑히면 표시해 표본에서 빠지지 않게 한다. */
  public void markSampled(String yearMonth) {
    if (yearMonth != null && yearMonth.matches("\\d{6}")) {
      this.sampledMonth = yearMonth;
    }
  }

  /** 원천 직무명에서 판정한 MVP 직군 코드를 붙인다. */
  public void classifyAs(String jobFamily) {
    this.jobFamily = jobFamily;
  }

  /** 원천 표기에서 판정한 고용형태 코드를 붙인다. 판정 불가는 `UNKNOWN` 이다. */
  public void classifyEmploymentTypeAs(String employmentType) {
    this.employmentType = employmentType;
  }

  /** 회사 규모 차원을 붙인다. 판정 근거가 없으면 null 이 아니라 `UNKNOWN` 이다 — 집계에서 NULL 은 전체 롤업을 뜻해 의미가 다르다. */
  public void classifyCompanySizeAs(String companySize) {
    this.companySize = companySize;
  }

  /**
   * 스킬 연결을 원천 재파싱 결과에 맞춘다. 전부 지우고 다시 넣지 않는 이유는 `(job_posting_id, skill_id)` 유니크 제약 때문이다 — 같은 플러시
   * 안에서 삭제보다 삽입이 먼저 나가면 그대로 남을 연결이 중복으로 걸린다. 빠질 것만 빼고 새것만 넣는다.
   */
  public void replaceSkills(List<Skill> skills) {
    if (skills == null) {
      skills = Collections.emptyList();
    }
    List<Skill> validSkills =
        skills.stream().filter(s -> s != null && s.getId() != null).collect(Collectors.toList());

    Set<UUID> targetIds = validSkills.stream().map(Skill::getId).collect(Collectors.toSet());
    jobPostingSkills.removeIf(
        link -> link.getSkill() == null || !targetIds.contains(link.getSkill().getId()));

    Set<UUID> linkedIds = linkedSkillIds();

    validSkills.stream()
        .filter(skill -> !linkedIds.contains(skill.getId()))
        .forEach(this::addSkill);
  }

  public void assignDedupGroup(UUID groupId, String ruleVersion) {
    this.dedupGroupId = groupId;
    this.dedupRuleVersion = ruleVersion;
  }

  /** 이 공고를 확인한 경로를 더한다. 이미 있으면 그대로 둔다. */
  public void addCollectedVia(String path) {
    if (path == null || path.isBlank() || hasCollectedVia(path)) {
      return;
    }
    this.collectedVia =
        collectedVia == null || collectedVia.isBlank() ? path : collectedVia + "," + path;
  }

  public boolean hasCollectedVia(String path) {
    return collectedVia != null && Arrays.asList(collectedVia.split(",")).contains(path);
  }

  /** 원천의 직군 태그 원본. 다중 직무 판단 근거로 보존한다. */
  public void recordSourceJobTags(String tags) {
    this.sourceJobTags = tags == null || tags.isBlank() ? null : tags;
  }

  private Set<UUID> linkedSkillIds() {
    return jobPostingSkills.stream()
        .filter(link -> link.getSkill() != null)
        .map(link -> link.getSkill().getId())
        .collect(Collectors.toCollection(HashSet::new));
  }

  /** 기존 연결은 두고 없는 스킬만 더한다 — 다른 원천이 붙인 스킬을 지우지 않는다. */
  public void addSkillsIfAbsent(List<Skill> skills) {
    Set<UUID> linked = linkedSkillIds();
    for (Skill skill : skills) {
      if (skill != null && skill.getId() != null && linked.add(skill.getId())) {
        addSkill(skill);
      }
    }
  }

  public void fillJobFamilyIfEmpty(String family) {
    if (jobFamily == null || jobFamily.isBlank()) {
      this.jobFamily = family;
    }
  }

  public void fillExperienceIfEmpty(Integer min, Integer max) {
    if (experienceMin == null && experienceMax == null) {
      this.experienceMin = min;
      this.experienceMax = max;
    }
  }

  public void applyPostedDates(LocalDate start, LocalDate end) {
    this.postedStartDate = start;
    this.postedEndDate = end;
  }

  public List<Skill> getSkills() {
    return jobPostingSkills.stream()
        .map(JobPostingSkill::getSkill)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
