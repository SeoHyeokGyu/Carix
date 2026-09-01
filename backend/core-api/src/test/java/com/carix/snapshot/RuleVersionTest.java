package com.carix.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleVersionTest {

  @Test
  @DisplayName("1.10.0 이 1.9.0 보다 크다 — 사전순 비교면 반대로 나온다")
  void compare_usesNumericSegmentsNotLexicographic() {
    assertThat(RuleVersion.of("1.10.0")).isGreaterThan(RuleVersion.of("1.9.0"));
  }

  @Test
  @DisplayName("major, minor, patch 순으로 비교한다")
  void compare_ordersBySegmentPrecedence() {
    assertThat(RuleVersion.of("2.0.0")).isGreaterThan(RuleVersion.of("1.99.99"));
    assertThat(RuleVersion.of("1.2.3")).isGreaterThan(RuleVersion.of("1.2.2"));
    assertThat(RuleVersion.of("1.2.3")).isEqualByComparingTo(RuleVersion.of("1.2.3"));
  }

  @Test
  @DisplayName("정렬하면 숫자 순서가 된다")
  void sort_ordersNumerically() {
    List<RuleVersion> sorted =
        List.of(RuleVersion.of("1.9.0"), RuleVersion.of("1.10.0"), RuleVersion.of("1.2.0")).stream()
            .sorted()
            .toList();

    assertThat(sorted).extracting(RuleVersion::value).containsExactly("1.2.0", "1.9.0", "1.10.0");
  }

  @Test
  @DisplayName("세 세그먼트가 아니거나 숫자가 아니면 거부한다")
  void of_rejectsMalformed() {
    assertThatThrownBy(() -> RuleVersion.of("1.2")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RuleVersion.of("1.2.x")).isInstanceOf(IllegalArgumentException.class);
  }
}
