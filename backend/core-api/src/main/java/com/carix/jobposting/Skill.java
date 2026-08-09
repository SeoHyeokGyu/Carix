package com.carix.jobposting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "skill")
public class Skill {

  @Id
  private UUID id;

  @Column(name = "standard_name", nullable = false)
  private String standardName;

  @Column
  private String category;

  @Column(name = "is_custom", nullable = false)
  private boolean isCustom;

  protected Skill() {
  }

  public Skill(String standardName, String category, boolean isCustom) {
    this.id = UUID.randomUUID();
    this.standardName = standardName;
    this.category = category;
    this.isCustom = isCustom;
  }

  public UUID getId() {
    return id;
  }

  public String getStandardName() {
    return standardName;
  }

  public String getCategory() {
    return category;
  }
}
