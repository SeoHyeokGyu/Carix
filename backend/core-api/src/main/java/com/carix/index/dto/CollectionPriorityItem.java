package com.carix.index.dto;

/**
 * 채용공고 시장 데이터 수집 우선순위 개별 항목 DTO (개발 계획 §2 b8b).
 *
 * <p>동료집단 차원(직군, 지역, 경력구간)별 집계 표본수를 바탕으로, MVI 산출 시 표본 부족(N &lt; 10) 및 완화 폴백이 발생하는 취약 차원을 도출하여 수집 타깃
 * 우선순위를 제시합니다.
 */
public record CollectionPriorityItem(
    String jobFamily,
    String region,
    String experienceBracket,
    int currentSampleCount,
    int requiredSampleSize,
    int sampleDeficit,
    PriorityGrade priorityGrade,
    String priorityReason,
    String searchKeywordHint) {

  public enum PriorityGrade {
    /** 표본 0건: 완전 결측 상태 (MVI 산출 불가, 최우선 긴급 수집 대상) */
    CRITICAL,
    /** 표본 1~4건: 극심한 표본 부족 (Level 4~5 완화 폴백 유발) */
    HIGH,
    /** 표본 5~9건: 최소 기준(10건) 미달 */
    MEDIUM,
    /** 표본 10건 이상: 최소 표본 기준 충족 (안정 상태) */
    SUFFICIENT
  }
}
