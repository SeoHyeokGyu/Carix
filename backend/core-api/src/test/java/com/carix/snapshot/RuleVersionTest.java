package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleVersionTest {

  @Test
  @DisplayName("1.10.0 이 1.9.0 보다 크다 — 사전순 비교면 반대로 나온다")
  void compare_usesNumericSegmentsNotLexicographic() {
    assertThat(RuleVersion.of("1.10.0")).isGreaterThan(RuleVersion.of("1.9.0"));
  }

  @Test
  @DisplayName("세 세그먼트가 아니거나 숫자가 아니면 거부한다")
  void of_rejectsMalformed() {
    assertThatThrownBy(() -> RuleVersion.of("1.2")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RuleVersion.of("1.2.x")).isInstanceOf(IllegalArgumentException.class);
  }
}
