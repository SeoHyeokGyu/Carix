package com.carix.jobposting;

import com.carix.skill.domain.Skill;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채용공고와 요구 스킬 간의 N:M 다대다 매핑 엔티티 (core.job_posting_skill).
 *
 * <p>스킬 보강 추천(US-D6) 시 동료집단 내에서 보유 스킬과 후보 스킬의 동시출현율(Co-occurrence Rate)을 산출할 때 활용됩니다.
 */
@Entity
@Table(schema = "core", name = "job_posting_skill")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobPostingSkill {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_posting_id", nullable = false)
  private JobPosting jobPosting;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "skill_id", nullable = false)
  private Skill skill;

  public JobPostingSkill(JobPosting jobPosting, Skill skill) {
    this(UUID.randomUUID(), jobPosting, skill);
  }
}
