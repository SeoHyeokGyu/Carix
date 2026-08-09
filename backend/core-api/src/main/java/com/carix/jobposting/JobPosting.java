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
import java.util.UUID;
import java.util.stream.Collectors;

@Entity
@Table(name = "job_posting")
public class JobPosting {

  @Id
  private UUID id;

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

  @Column
  private String region;

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

  @Column
  private String content;

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

  protected JobPosting() {
  }

  public JobPosting(String source, String externalId, String title, String companyName,
      Long salaryMin, Long salaryMax, String region, Integer experienceMin,
      Integer experienceMax, String employmentType, String companySize, String companyBadge,
      String positionLevel, String jobFamily, String content, LocalDate postedStartDate,
      LocalDate postedEndDate, String firstSeenMonth, String lastSeenMonth,
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
    this.content = content;
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

  public UUID getId() {
    return id;
  }

  public Long getSalaryMin() {
    return salaryMin;
  }

  public String getPositionLevel() {
    return positionLevel;
  }

  public List<Skill> getSkills() {
    return jobPostingSkills.stream().map(JobPostingSkill::getSkill).collect(Collectors.toList());
  }
}
