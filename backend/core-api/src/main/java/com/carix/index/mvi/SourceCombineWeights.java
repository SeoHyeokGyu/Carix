package com.carix.index.mvi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 원천별 시장가치 결과를 합칠 때의 가중치. 무작위 표본 원천(잡코리아·원티드)은 동료집단 표본 수 그대로, 정기 수집 원천은 <b>합계</b>가 같은 계산의 표본 원천 가장
 * 큰 표본 수를 넘지 않게 비례로 줄인다 — 원천별로 따로 묶으면 정기 수집 원천 둘이 표본 원천의 2배까지 커진다(프레임 설계 §4.2). 표본 원천이 없으면 상한도 없다.
 * 호출하는 쪽은 스킬마다 그 스킬에 지수를 낸 원천만 넘긴다 — 결합에서 빠진 원천이 상한 몫을 차지하거나 상한을 정하지 않게.
 */
public final class SourceCombineWeights {

  public static final Set<String> SAMPLED_SOURCES = Set.of("JOBKOREA", "WANTED");

  private SourceCombineWeights() {}

  public static Map<String, Double> of(Map<String, Integer> sampleCounts) {
    Map<String, Integer> valid = new LinkedHashMap<>();
    sampleCounts.forEach(
        (source, count) -> {
          if (count != null && count > 0) {
            valid.put(source, count);
          }
        });
    int cap =
        valid.entrySet().stream()
            .filter(e -> SAMPLED_SOURCES.contains(e.getKey()))
            .mapToInt(Map.Entry::getValue)
            .max()
            .orElse(Integer.MAX_VALUE);
    long regularTotal =
        valid.entrySet().stream()
            .filter(e -> !SAMPLED_SOURCES.contains(e.getKey()))
            .mapToLong(Map.Entry::getValue)
            .sum();
    double scale = regularTotal > cap ? (double) cap / regularTotal : 1.0;
    Map<String, Double> out = new LinkedHashMap<>();
    valid.forEach(
        (source, count) ->
            out.put(source, SAMPLED_SOURCES.contains(source) ? count : count * scale));
    return out;
  }
}
