package com.carix.index.mvi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SourceCombineWeightsTest {

  @Test
  @DisplayName("정기 수집 원천은 표본 원천 건수로 묶인다")
  void capsCrawlSourceAtSampledCount() {
    assertThat(SourceCombineWeights.of(Map.of("JOBKOREA", 60, "SARAMIN", 2000)))
        .containsEntry("JOBKOREA", 60.0)
        .containsEntry("SARAMIN", 60.0);
  }

  @Test
  @DisplayName("정기 수집 원천이 표본 원천보다 작으면 그대로다")
  void belowCapUnchanged() {
    assertThat(SourceCombineWeights.of(Map.of("JOBKOREA", 400, "SARAMIN", 120)))
        .containsEntry("JOBKOREA", 400.0)
        .containsEntry("SARAMIN", 120.0);
  }

  @Test
  @DisplayName("표본 원천이 없으면 정기 수집 원천은 그대로다")
  void noSampledSourceNoCap() {
    assertThat(SourceCombineWeights.of(Map.of("SARAMIN", 2000))).containsEntry("SARAMIN", 2000.0);
  }

  @Test
  @DisplayName("표본이 0 인 원천은 빠지고 상한 계산에도 안 쓴다")
  void dropsEmpty() {
    assertThat(SourceCombineWeights.of(Map.of("JOBKOREA", 0, "SARAMIN", 30)))
        .containsOnlyKeys("SARAMIN")
        .containsEntry("SARAMIN", 30.0);
  }
}
