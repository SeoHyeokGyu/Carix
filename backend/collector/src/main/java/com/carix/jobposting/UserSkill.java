package com.carix.jobposting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "user_skill",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "skill_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class UserSkill {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "skill_id", nullable = false)
  private Skill skill;

  @Column(name = "months_of_experience", nullable = false)
  private int monthsOfExperience;

  @Column(name = "last_used_date", nullable = false)
  private LocalDate lastUsedDate;
}
