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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "job_posting")
public class JobPosting {

  @Id private UUID id;

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

  @Column(name = "collected_at", nullable = false)
  private Instant collectedAt;

  @Column(name = "dedup_group_id")
  private UUID dedupGroupId;

  @Column(name = "dedup_rule_version", length = 20)
  private String dedupRuleVersion;

  @OneToMany(mappedBy = "jobPosting", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<JobPostingSkill> jobPostingSkills = new ArrayList<>();

  protected JobPosting() {}

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
    this.salaryMin = salaryMin;
    this.salaryMax = salaryMax;
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
    this.dedupGroupId = UUID.randomUUID();
    this.dedupRuleVersion = "v0-none";
  }

  public void addSkill(Skill skill) {
    jobPostingSkills.add(new JobPostingSkill(this, skill));
  }

  /** 원천이 공개한 세전 연봉 범위(만원). 비공개 공고는 호출하지 않는다. */
  public void applyDisclosedSalary(Long min, Long max) {
    this.salaryMin = min;
    this.salaryMax = max;
  }

  /** 공고가 요구하는 경력 연차 범위. 하한이 동료집단 브래킷의 기준이다 — 공고가 받아 주는 가장 낮은 연차로 비교한다. */
  public void applyExperienceRange(Integer min, Integer max) {
    this.experienceMin = min;
    this.experienceMax = max;
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
    Set<UUID> targetIds = skills.stream().map(Skill::getId).collect(Collectors.toSet());
    jobPostingSkills.removeIf(link -> !targetIds.contains(link.getSkill().getId()));

    Set<UUID> linkedIds =
        jobPostingSkills.stream().map(link -> link.getSkill().getId()).collect(Collectors.toSet());
    skills.stream().filter(skill -> !linkedIds.contains(skill.getId())).forEach(this::addSkill);
  }

  public UUID getId() {
    return id;
  }

  public Long getSalaryMin() {
    return salaryMin;
  }

  public Integer getExperienceMin() {
    return experienceMin;
  }

  public Integer getExperienceMax() {
    return experienceMax;
  }

  public String getEmploymentType() {
    return employmentType;
  }

  public String getPositionLevel() {
    return positionLevel;
  }

  public List<Skill> getSkills() {
    return jobPostingSkills.stream().map(JobPostingSkill::getSkill).collect(Collectors.toList());
  }

  public String getSource() {
    return source;
  }

  public String getExternalId() {
    return externalId;
  }
}
