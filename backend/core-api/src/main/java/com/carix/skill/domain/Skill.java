package com.carix.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "skill", schema = "core")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@EqualsAndHashCode(of = "id")
public class Skill {

  @Id
  @Column(name = "id", nullable = false)
  @Builder.Default
  private UUID id = UUID.randomUUID();

  @Column(name = "standard_name", nullable = false, length = 255)
  private String standardName;

  @Column(name = "category", length = 50)
  private String category;

  @Column(name = "is_custom", nullable = false)
  private boolean isCustom;

  @Column(name = "created_by")
  private UUID createdBy;

  public static Skill createCustom(String standardName, String category, UUID createdBy) {
    return Skill.builder()
        .id(UUID.randomUUID())
        .standardName(standardName)
        .category(category)
        .isCustom(true)
        .createdBy(createdBy)
        .build();
  }

  public static Skill createStandard(String standardName, String category) {
    return Skill.builder()
        .id(UUID.randomUUID())
        .standardName(standardName)
        .category(category)
        .isCustom(false)
        .createdBy(null)
        .build();
  }
}
