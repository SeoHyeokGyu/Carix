package com.carix.index;

/**
 * 연봉 격차 판정의 기준선. 공식 임금통계를 쓰고, 공고 공개연봉은 참고 표시 전용이다 (README 데이터 원칙).
 *
 * @param medianGrossSalary 동료집단 조건의 세전 연봉 중앙값. 단위는 원이다
 * @param wageDefinition 임금 정의 문구. 예: `초과급여 제외, 상여 포함`
 * @param statVintage 통계 개정판. {@code YYYYMM} 6자다. 같은 기준기간의 개정판이 공존하므로 어느 판을 썼는지 남긴다
 */
public record WageBaseline(long medianGrossSalary, String wageDefinition, String statVintage) {}
