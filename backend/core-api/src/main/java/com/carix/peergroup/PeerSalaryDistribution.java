package com.carix.peergroup;

import com.carix.index.mvi.PeerGroupFallbackResolver;
import java.util.Collections;
import java.util.List;

/**
 * 한 사용자의 동료집단 공고 분포.
 *
 * <p>대시보드 §5.2 와 시나리오 §6.1 이 같은 분포를 서로 다르게 읽는다 — 전자는 5수치와 사용자 위치를, 후자는 이직 목표 연봉의 백분위를 낸다. 응답 DTO 를
 * 공유하면 한쪽 화면 사정이 다른 쪽 계약을 흔들므로, 공유하는 것은 원자료인 이 값이다.
 *
 * @param disclosedSalaries 유효 공개연봉만 오름차순으로. 동료집단 공고 수보다 작다
 * @param peerSampleSize 중복 제거 후 동료집단 공고 수. 공개연봉 건수가 아니다
 * @param fallbackLevel 안착한 완화 단계 (1~5)
 * @param appliedConditions 화면에 그대로 쓸 비교 조건 문구
 * @param trend 등록월(posted_start_date)별 제시 연봉 추이 (기능 명세 §4.6)
 */
public record PeerSalaryDistribution(
    List<Long> disclosedSalaries,
    int peerSampleSize,
    int fallbackLevel,
    String appliedConditions,
    List<TrendPoint> trend) {

  public record TrendPoint(
      String yearMonth, Long median, int sampleSize, int disclosedSampleSize, double missingRate) {}

  public PeerSalaryDistribution(
      List<Long> disclosedSalaries,
      int peerSampleSize,
      int fallbackLevel,
      String appliedConditions) {
    this(
        disclosedSalaries,
        peerSampleSize,
        fallbackLevel,
        appliedConditions,
        Collections.emptyList());
  }

  /** 동료집단이 최소 표본을 채웠는가. 공개연봉 건수는 따로 본다. */
  public boolean hasSufficientPeers() {
    return peerSampleSize >= PeerGroupFallbackResolver.MIN_SAMPLE_SIZE;
  }

  /** 공개연봉이 최소 표본을 채웠는가. 5수치·백분위는 이 표본 위에서 매기므로 연봉 분포·실현 가능성은 이것까지 본다. */
  public boolean hasSufficientSalaries() {
    return disclosedSalaries.size() >= PeerGroupFallbackResolver.MIN_SAMPLE_SIZE;
  }

  /** 연봉이 공개된 공고 수. 백분위는 이 표본 위에서 매겨진다. */
  public int disclosedSampleSize() {
    return disclosedSalaries.size();
  }

  /**
   * 값보다 낮은 공개연봉의 비율(%).
   *
   * <p>{@code percentile_cont} 대신 nearest-rank 다 — 5수치와 같은 방식이라야 "상위 30%"와 5수치가 서로 어긋나지 않는다.
   */
  public int percentileOf(long salary) {
    long below = disclosedSalaries.stream().filter(value -> value < salary).count();
    return (int) Math.round((double) below / disclosedSalaries.size() * 100);
  }
}
