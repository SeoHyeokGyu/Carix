package com.carix.jobposting;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "job_posting_skill")
public class JobPostingSkill {

  @Id
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "job_posting_id", nullable = false)
  private JobPosting jobPosting;

  @ManyToOne
  @JoinColumn(name = "skill_id", nullable = false)
  private Skill skill;

  protected JobPostingSkill() {
  }

  JobPostingSkill(JobPosting jobPosting, Skill skill) {
    this.id = UUID.randomUUID();
    this.jobPosting = jobPosting;
    this.skill = skill;
  }

  public Skill getSkill() {
    return skill;
  }
}
