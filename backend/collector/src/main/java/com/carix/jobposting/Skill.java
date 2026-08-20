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

  /** 사용자가 직접 만든 스킬의 소유자. 수집으로 관측된 스킬은 비어 있다. */
  @Column(name = "created_by")
  private UUID createdBy;

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

  public boolean isCustom() {
    return isCustom;
  }

  public void recategorize(String category) {
    this.category = category;
  }

  /**
   * 채용공고 수집으로 관측된 스킬임을 표시한다. `is_custom` 은 사용자가 사전에 없는 스킬을
   * 직접 입력한 경우를 뜻하므로, 시장 집계 대상인 수집 스킬에는 붙으면 안 된다.
   */
  public void markAsMarketObserved() {
    this.isCustom = false;
  }
}
