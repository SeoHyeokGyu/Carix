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

  @Test
  @DisplayName("정기 수집 원천 둘은 합계로 묶여 표본 원천 최대치를 넘지 않는다")
  void capsRegularSourcesAsPool() {
    assertThat(SourceCombineWeights.of(Map.of("JOBKOREA", 60, "SARAMIN", 90, "JUMPIT", 30)))
        .containsEntry("JOBKOREA", 60.0)
        .containsEntry("SARAMIN", 45.0)
        .containsEntry("JUMPIT", 15.0);
  }

  @Test
  @DisplayName("원티드도 표본 원천이라 상한은 잡코리아·원티드 중 큰 쪽이다")
  void wantedIsSampledSource() {
    assertThat(
            SourceCombineWeights.of(
                Map.of("JOBKOREA", 60, "WANTED", 100, "SARAMIN", 90, "JUMPIT", 30)))
        .containsEntry("JOBKOREA", 60.0)
        .containsEntry("WANTED", 100.0)
        .containsEntry("SARAMIN", 75.0)
        .containsEntry("JUMPIT", 25.0);
  }

  @Test
  @DisplayName("정기 수집 원천이 표본 원천보다 작으면 그대로다")
  void belowCapUnchanged() {
    assertThat(SourceCombineWeights.of(Map.of("JOBKOREA", 400, "SARAMIN", 120)))
        .containsEntry("JOBKOREA", 400.0)
        .containsEntry("SARAMIN", 120.0);
  }

  @Test
  @DisplayName("프로그래머스도 정기 수집 원천 풀에 포함되어 상한 적용을 받는다")
  void programmersIncludedInRegularPool() {
    // cap = max(100, 60) = 100. regularTotal = 100(SARAMIN) + 60(JUMPIT) + 40(PROGRAMMERS) = 200.
    // scale = 100/200 = 0.5
    assertThat(
            SourceCombineWeights.of(
                Map.of(
                    "JOBKOREA", 60,
                    "WANTED", 100,
                    "SARAMIN", 100,
                    "JUMPIT", 60,
                    "PROGRAMMERS", 40)))
        .containsEntry("JOBKOREA", 60.0)
        .containsEntry("WANTED", 100.0)
        .containsEntry("SARAMIN", 50.0)
        .containsEntry("JUMPIT", 30.0)
        .containsEntry("PROGRAMMERS", 20.0);
  }
}
