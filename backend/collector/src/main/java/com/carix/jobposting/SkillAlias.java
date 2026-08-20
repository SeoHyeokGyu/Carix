package com.carix.jobposting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "skill_alias")
public class SkillAlias {

  @Id
  private UUID id;

  @ManyToOne
  @JoinColumn(name = "skill_id", nullable = false)
  private Skill skill;

  @Column(name = "alias_name", nullable = false)
  private String aliasName;

  protected SkillAlias() {
  }

  public SkillAlias(Skill skill, String aliasName) {
    this.id = UUID.randomUUID();
    this.skill = skill;
    this.aliasName = aliasName;
  }
}
