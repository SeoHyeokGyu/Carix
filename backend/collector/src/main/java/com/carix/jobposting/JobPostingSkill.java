package com.carix.jobposting;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "job_posting_skill",
    uniqueConstraints =
        @UniqueConstraint(
            name = "job_posting_skill_job_posting_id_skill_id_key",
            columnNames = {"job_posting_id", "skill_id"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobPostingSkill {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  @ManyToOne
  @JoinColumn(name = "job_posting_id", nullable = false)
  private JobPosting jobPosting;

  @ManyToOne
  @JoinColumn(name = "skill_id", nullable = false)
  private Skill skill;

  JobPostingSkill(JobPosting jobPosting, Skill skill) {
    this(UUID.randomUUID(), jobPosting, skill);
  }
}
