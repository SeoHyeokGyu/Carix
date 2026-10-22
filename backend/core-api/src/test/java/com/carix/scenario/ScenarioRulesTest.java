package com.carix.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 규격이 고정한 결과 유형 경계와 실업급여 가정치 한도 (도메인 정의서 §4). */
class ScenarioRulesTest {

  private final UnemploymentBenefitAssumption assumption =
      new UnemploymentBenefitAssumption(66_000L, 60_120L, "2026-01");

  @Test
  @DisplayName("차이가 정확히 0이면 추가보상, 음수면 기회비용이다")
  void 결과유형_경계() {
    assertThat(ScenarioCalculator.typeLabelOf(0)).isEqualTo("추정 추가보상");
    assertThat(ScenarioCalculator.typeLabelOf(-1)).isEqualTo("추정 기회비용");
  }

  @Test
  @DisplayName("일 지급액이 상한을 넘으면 상한으로 자른다")
  void 일지급액_상한() {
    assertThat(assumption.monthlyBenefit(200_000_000L)).isEqualTo(66_000L * 30);
  }

  @Test
  @DisplayName("일 지급액이 하한에 못 미치면 하한으로 올린다")
  void 일지급액_하한() {
    assertThat(assumption.monthlyBenefit(20_000_000L)).isEqualTo(60_120L * 30);
  }

  @Test
  @DisplayName("상·하한 사이면 평균임금의 60%를 쓴다")
  void 일지급액_구간내() {
    assertThat(assumption.dailyBenefit(40_000_000L)).isEqualTo(65_753L);
  }

  @Test
  @DisplayName("공백이 6개월이어도 급여는 5개월에서 끊긴다")
  void 수급_상한월수() {
    assertThat(UnemploymentBenefitAssumption.benefitMonths(6)).isEqualTo(5);
    assertThat(UnemploymentBenefitAssumption.benefitMonths(3)).isEqualTo(3);
  }

  @Test
  @DisplayName("수급 자격이 없으면 실업급여 미반영 안내다")
  void 미수급_안내() {
    assertThat(new ScenarioCalculator(assumption).benefitNote(false)).isEqualTo("실업급여 미반영");
  }
}
