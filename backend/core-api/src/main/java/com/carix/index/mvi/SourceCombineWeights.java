package com.carix.index.mvi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 원천별 시장가치 결과를 합칠 때의 가중치. 무작위 표본 원천은 동료집단 표본 수 그대로, 정기 수집 원천은 같은 계산의 표본 원천 가장 큰 표본 수를 상한으로 센다 — 정기
 * 수집분이 결합을 지배해 지수가 목록 쏠림으로 끌려가는 것을 막는다(원티드·점핏 설계 §6). 표본 원천이 없으면 상한도 없다.
 */
final class SourceCombineWeights {

  static final Set<String> SAMPLED_SOURCES = Set.of("JOBKOREA");

  private SourceCombineWeights() {}

  static Map<String, Double> of(Map<String, Integer> sampleCounts) {
    int cap =
        sampleCounts.entrySet().stream()
            .filter(e -> SAMPLED_SOURCES.contains(e.getKey()))
            .mapToInt(e -> e.getValue() == null ? 0 : e.getValue())
            .filter(c -> c > 0)
            .max()
            .orElse(Integer.MAX_VALUE);
    Map<String, Double> out = new LinkedHashMap<>();
    sampleCounts.forEach(
        (source, count) -> {
          if (count == null || count <= 0) {
            return;
          }
          int weight = SAMPLED_SOURCES.contains(source) ? count : Math.min(count, cap);
          out.put(source, (double) weight);
        });
    return out;
  }
}
