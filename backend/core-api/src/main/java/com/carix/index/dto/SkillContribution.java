package com.carix.index.dto;

import java.math.BigDecimal;

/**
 * 한 스킬이 시장가치 지수에 얼마나 기여했는지 (개발 계획 §5-M).
 *
 * <p>{@code rationale} 은 지금까지 빠진 스킬의 *이름*만 남겨, 어떤 스킬이 지수를 끌어올렸는지 시간축으로 볼 수 없었다. 이 값은 {@code
 * MviResult} 안에만 있고 저장되지 않으면 소급이 불가능하다 — 기록하지 않는 달의 기여도는 영원히 없다.
 *
 * <p>금액이 아니라 지수와 가중치라 §5-L 의 복원 경로를 더하지 않는다.
 *
 * @param standardName 표준 스킬명. 식별자 대신 이름을 쓰는 이유는 {@code excludedSkills} 와 같다 — 이력 화면이 그대로 쓴다
 * @param weight 재정규화된 숙련도 가중치. 유효 스킬 전체의 합이 1.0 이다
 * @param demandIndex 원천 표본수로 가중 결합한 스킬 수요 지수
 * @param contribution {@code weight × demandIndex}. 지수 기여분이다
 */
public record SkillContribution(
    String standardName, BigDecimal weight, BigDecimal demandIndex, BigDecimal contribution) {}
