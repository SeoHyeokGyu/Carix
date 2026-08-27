package com.carix.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 계약의 열거형 값을 명세에 고정한다.
 *
 * <p>값 이름이 바뀌면 A·B·C 세 도메인이 동시에 깨진다. 명세에서 고정한 어휘라 테스트로 못 박는다.
 */
class IndexContractVocabularyTest {

  @Test
  void 소비_가중치_상태는_도메인_정의서_4종이다() {
    assertThat(Arrays.stream(ExpenseWeightStatus.values()).map(Enum::name))
        .containsExactlyInAnyOrder(
            "USER_DEFINED", "PARTIALLY_ESTIMATED", "PROPAGATED", "DEFAULT_CPI");
  }

  @Test
  void 스킬_제외_사유는_기능_명세의_네_가지다() {
    assertThat(Arrays.stream(SkillExclusionReason.values()).map(Enum::name))
        .containsExactlyInAnyOrder(
            "CUSTOM_SKILL_NO_STATS", "INSUFFICIENT_SAMPLE", "NEW_OBSERVATION", "NO_RECENT_DEMAND");
  }

  @Test
  void 지수_상태는_표본부족과_원천지연을_구분한다() {
    assertThat(Arrays.stream(IndexStatus.values()).map(Enum::name))
        .containsExactlyInAnyOrder(
            "OK", "INSUFFICIENT_SAMPLE", "UPDATE_DELAYED", "INSUFFICIENT_HISTORY", "NOT_AVAILABLE");
  }
}
