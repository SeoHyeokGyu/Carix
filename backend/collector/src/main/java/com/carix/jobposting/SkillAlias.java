package com.carix.jobposting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "skill_alias")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SkillAlias {

  @Id @Builder.Default private UUID id = UUID.randomUUID();

  @ManyToOne
  @JoinColumn(name = "skill_id", nullable = false)
  private Skill skill;

  @Column(name = "alias_name", nullable = false)
  private String aliasName;

  public SkillAlias(Skill skill, String aliasName) {
    this(UUID.randomUUID(), skill, aliasName);
  }
}
