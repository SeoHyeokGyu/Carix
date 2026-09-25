package com.carix.index.dto;

import com.carix.wagestat.WageStatSource;

/**
 * 연봉 격차 판정의 기준선. 공식 임금통계를 쓰고, 공고 공개연봉은 참고 표시 전용이다 (README 데이터 원칙).
 *
 * @param medianGrossSalary 동료집단 조건의 세전 연봉 중앙값. 단위는 원이다
 * @param wageDefinition 임금 정의 문구. 예: {@code "초과급여 제외, 상여 포함"}
 * @param statVintage 통계 개정판. {@code YYYYMM} 6자다. 같은 기준기간의 개정판이 공존하므로 어느 판을 썼는지 남긴다
 * @param source 관측치 출처. {@link WageStatSource}
 */
public record WageBaseline(
    long medianGrossSalary, String wageDefinition, String statVintage, WageStatSource source) {

  public WageBaseline(long medianGrossSalary, String wageDefinition, String statVintage) {
    this(medianGrossSalary, wageDefinition, statVintage, WageStatSource.UNKNOWN);
  }

  /**
   * 민감 금액이 평문으로 로그에 찍히는 것을 방지한다 (완료 기준 10번).
   *
   * <p>임금통계 중앙값 자체는 개인정보가 아니지만, 연봉 격차 비율과 중앙값이 동시에 로그에 찍히면 {@code 사용자 연봉 = 중앙값 / (1 + 격차비율)} 로
   * 복원되므로 마스킹한다.
   */
  @Override
  public String toString() {
    return "WageBaseline[wageDefinition="
        + wageDefinition
        + ", statVintage="
        + statVintage
        + ", source="
        + source
        + ", medianGrossSalary=***]";
  }
}
