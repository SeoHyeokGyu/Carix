package com.carix.pipeline.dto;

/**
 * 채용공고 시장 데이터 수집 우선순위 개별 항목.
 *
 * <p>동료집단 차원(직군, 지역, 경력구간)별 집계 표본수를 바탕으로, MVI 산출 시 표본 부족(N &lt; 10) 및 완화 폴백이 발생하는 취약 차원을 도출하여 크롤러의
 * 수집 타깃 우선순위를 제시합니다.
 *
 * @param jobFamily 직무 카테고리 (예: DEVELOPER, DATA 등)
 * @param region 지역명 (예: 서울 강남구, 서울 전체, 전국 등)
 * @param experienceBracket 경력 구간 브래킷 (ENTRY, JUNIOR, MID, SENIOR, LEAD)
 * @param currentSampleCount 현재 적재된 표본 공고 건수
 * @param requiredSampleSize MVI 산출에 필요한 최소 기준 표본수 (기본 10)
 * @param sampleDeficit 부족 표본 건수 (max(0, required - current))
 * @param priorityGrade 우선순위 등급 (CRITICAL, HIGH, MEDIUM, SUFFICIENT)
 * @param priorityReason 우선순위 부여 사유 문구
 * @param searchKeywordHint 크롤러 타깃 검색 키워드 힌트
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
    /** 표본 0건: 완전 결측 상태로 MVI 산출 불가 (최우선 긴급 수집 대상) */
    CRITICAL,
    /** 표본 1~4건: 극심한 표본 부족 (Level 4~5 완화 폴백 유발) */
    HIGH,
    /** 표본 5~9건: 최소 기준(10건) 미달 (표본 보강 필요) */
    MEDIUM,
    /** 표본 10건 이상: 최소 표본 기준 충족 (안정 상태) */
    SUFFICIENT
  }
}
