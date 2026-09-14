package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결정 신호의 한글 문구는 여기 한 곳에만 있다.
 *
 * <p>문구가 서버 서비스와 프런트 화면에 따로 적히면 한쪽만 바뀐다 — 실제로 그랬다. 서버는 {@code "스킬 보강"} 을, {@code history.tsx} 는
 * {@code "스킬 보강 우선"} 을 쓰고 있었다. 캐노니컬은 MVP 요구사항 §193-196 이다.
 */
class DecisionSignalCodeTest {

  @Test
  @DisplayName("다섯 신호의 문구가 MVP 요구사항의 캐노니컬 표기와 같다")
  void 캐노니컬_문구() {
    assertThat(DecisionSignalCode.SEEK_JOB.label()).isEqualTo("이직 탐색");
    assertThat(DecisionSignalCode.NEGOTIATE.label()).isEqualTo("협상 검토");
    assertThat(DecisionSignalCode.UPSKILL.label()).isEqualTo("스킬 보강 우선");
    assertThat(DecisionSignalCode.STAY.label()).isEqualTo("현 상태 유지");
    assertThat(DecisionSignalCode.INSUFFICIENT_DATA.label()).isEqualTo("데이터 부족");
  }

  @Test
  @DisplayName("문구가 비어 있는 신호는 없다")
  void 빈_문구_없음() {
    for (DecisionSignalCode code : DecisionSignalCode.values()) {
      assertThat(code.label()).isNotBlank();
    }
  }
}
