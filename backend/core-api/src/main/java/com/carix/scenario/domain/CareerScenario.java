package com.carix.scenario.domain;

import com.carix.common.EncryptedConverter;
import com.carix.common.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

/**
 * 저장된 시나리오 시뮬레이션 1건 (API 규격 §6.1 — "계산 성공 시 입력 가정과 결과를 저장합니다").
 *
 * <p><b>금액 컬럼은 전부 암호화한다.</b> 평문으로 두면 {@code stayCompensation12m} 을 {@code stayExpectedRaiseRate} 로
 * 나눠 현재 연봉이 원 단위까지 복원되고, {@code moveTargetGrossSalary} 는 아예 그대로 노출된다 — {@code
 * income_history.gross_salary_encrypted} 를 AES-256-GCM 으로 감싼 이유가 이 테이블에서 사라진다. 금액으로 조회하거나 정렬하는 경로가
 * 없어(인덱스는 {@code user_id, calculated_at DESC, id DESC} 뿐) 암호화의 질의 비용은 없다.
 *
 * <p>엔진 2(잔류·이직 세후 평가)로 저장한 행은 엔진 1 컬럼이 비고 {@code payload_encrypted} 에 요청·결과 JSON 이 암호문으로 들어간다. 엔진
 * 1 행은 새로 만들지 않고 목록에서 읽기만 한다.
 *
 * <p>{@code moveOneTimeCost} 만 평문이다. 사용자가 입력한 부대비용이라 연봉 복원에 쓰이지 않는다.
 *
 * <p>실현 가능성은 재계산하지 않고 저장한다. 동료집단 분포는 그 시점의 공고에 의존해 나중에 다시 구하면 사용자가 실제로 본 판단이 나오지 않는다 — 스냅샷 {@code
 * rationale} 이 원천별 커버리지를 저장분 그대로 읽는 것과 같은 이유다.
 */
@Entity
@Table(name = "career_scenario", schema = "core")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CareerScenario {

  public static final String ENGINE_LEGACY = "1";
  public static final String ENGINE_STAY_OR_MOVE = "2";

  @Id @GeneratedValue private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "scenario_name", length = 100)
  private String scenarioName;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "current_gross_salary_encrypted", length = 512)
  private Long currentGrossSalary;

  @Column(name = "stay_expected_raise_rate", precision = 5, scale = 2)
  private BigDecimal stayExpectedRaiseRate;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "move_target_gross_salary_encrypted", length = 512)
  private Long moveTargetGrossSalary;

  @Column(name = "move_one_time_cost")
  private Long moveOneTimeCost;

  @Column(name = "expected_unemployment_months")
  private Integer expectedUnemploymentMonths;

  @Column(name = "is_eligible_for_unemployment_benefit")
  private Boolean eligibleForUnemploymentBenefit;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "stay_compensation_12m_encrypted", length = 512)
  private Long stayCompensation12m;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "move_compensation_12m_encrypted", length = 512)
  private Long moveCompensation12m;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "estimated_unemployment_benefit_encrypted", length = 512)
  private Long estimatedUnemploymentBenefit;

  @Convert(converter = EncryptedConverter.class)
  @Column(name = "difference_encrypted", length = 512)
  private Long difference;

  @Column(name = "feasibility_status", length = 30)
  private String feasibilityStatus;

  /** 표본이 부족해 산출하지 못했으면 {@code null} 이다. 0 이나 50 으로 채우지 않는다. */
  @Column(name = "feasibility_percentile")
  private Integer feasibilityPercentile;

  @Column(name = "feasibility_sample_size")
  private Integer feasibilitySampleSize;

  /**
   * 계산 방식. {@link #ENGINE_LEGACY} 는 위 엔진 1 컬럼을, {@link #ENGINE_STAY_OR_MOVE} 는 {@link #payload} 만
   * 쓴다.
   */
  @Builder.Default
  @ColumnDefault("'1'")
  @Column(name = "engine_version", nullable = false, length = 10)
  private String engineVersion = ENGINE_LEGACY;

  /** 편집 중인 입력. 사용자당 1건이고 목록에 나오지 않는다. 엔진 2 전용이다. */
  @Builder.Default
  @ColumnDefault("false")
  @Column(name = "is_draft", nullable = false)
  private boolean draft = false;

  /** 엔진 2 의 {@code {request, result}} JSON. 초안은 {@code result} 가 없다. 컨버터가 암호화한다. */
  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "payload_encrypted", columnDefinition = "text")
  private String payload;

  @Column(name = "calculated_at", nullable = false, updatable = false)
  private OffsetDateTime calculatedAt;

  /** 초안을 덮어쓴다. 엔티티를 새로 만들지 않아 사용자당 1건이 유지된다. */
  public void replacePayload(String newPayload) {
    this.payload = newPayload;
  }

  /**
   * 계산 시각을 앱에서 채운다.
   *
   * <p>DDL 의 {@code DEFAULT CURRENT_TIMESTAMP} 에 맡기면 통합 테스트가 이 값을 못 본다 — H2 스키마는 엔티티에서 생성돼 기본값이 없고,
   * 그러면 {@code calculated_at DESC} 정렬이 검증되지 않은 채 운영에서만 돈다. DDL 의 기본값은 직접 INSERT 하는 경우를 위해 남겨 둔다.
   */
  @PrePersist
  void fillCalculatedAt() {
    if (calculatedAt == null) {
      calculatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
  }
}
