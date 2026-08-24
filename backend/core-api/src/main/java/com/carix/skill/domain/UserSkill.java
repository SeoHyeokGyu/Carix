package com.carix.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "user_skill",
    schema = "core",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"user_id", "skill_id"})})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(of = "id")
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

  public UserSkill(UUID userId, Skill skill, int monthsOfExperience, LocalDate lastUsedDate) {
    this(UUID.randomUUID(), userId, skill, monthsOfExperience, lastUsedDate);
  }

  public void update(int monthsOfExperience, LocalDate lastUsedDate) {
    if (monthsOfExperience < 1 || monthsOfExperience > 600) {
      throw new IllegalArgumentException("실무 경험 개월 수는 1~600 범위여야 합니다.");
    }
    this.monthsOfExperience = monthsOfExperience;
    this.lastUsedDate = Objects.requireNonNull(lastUsedDate, "마지막 사용일은 필수입니다.");
  }
}
