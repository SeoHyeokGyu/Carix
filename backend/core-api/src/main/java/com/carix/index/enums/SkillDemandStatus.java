package com.carix.index.enums;

import java.util.Optional;

/**
 * 스킬 수요 지수 산출 결과 상태.
 *
 * <ul>
 *   <li>{@link #OK}: 정상 산출 완료
 *   <li>{@link #NEW_OBSERVATION}: 분모 0 (이전 12개월 관측 없음, 신규 관측 스킬 -> 가중합 제외)
 *   <li>{@link #INSUFFICIENT_SAMPLE}: 스킬 행 표본 수 미달 (가중합 제외)
 *   <li>{@link #NO_RECENT_DEMAND}: 분자 0 (최근 3개월 관측 없음, 지수 0으로 가중합 반영)
 * </ul>
 */
public enum SkillDemandStatus {
  /** 정상 산출 완료 */
  OK(null),

  /** 분모 0: 이전 12개월(4~15개월 전) 관측이 없어 분모가 0이다. 화면 표기는 `신규 관측` (§4.1). */
  NEW_OBSERVATION(SkillExclusionReason.NEW_OBSERVATION),

  /** 스킬 행 표본이 기간 창별 기준 미달이다 (§2.1). */
  INSUFFICIENT_SAMPLE(SkillExclusionReason.INSUFFICIENT_SAMPLE),

  /** 최근 3개월 관측이 없다. 지수 0으로 가중합에 반영하되 화면에서 구분한다 (§4.1). */
  NO_RECENT_DEMAND(SkillExclusionReason.NO_RECENT_DEMAND);

  private final SkillExclusionReason exclusionReason;

  SkillDemandStatus(SkillExclusionReason exclusionReason) {
    this.exclusionReason = exclusionReason;
  }

  /** 해당 상태에 대응하는 스킬 제외 사유를 반환합니다. 정상 산출({@link #OK}) 상태인 경우 빈 Optional을 반환합니다. */
  public Optional<SkillExclusionReason> getExclusionReason() {
    return Optional.ofNullable(exclusionReason);
  }

  /** 정상 산출 상태인지 여부를 반환합니다. */
  public boolean isOk() {
    return this == OK;
  }
}
