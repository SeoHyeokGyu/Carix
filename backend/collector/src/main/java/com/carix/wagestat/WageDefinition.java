package com.carix.wagestat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공식 임금 통계의 산정 기준 및 정의(Wage Definition)를 나타내는 Enum.
 *
 * <p>통계청(KOSIS) 등 외부 원천 데이터의 임금 집계 방식과 사용자 입력(세전 연봉) 간의 차이를 명확히 구분하고, 향후 다양한 임금 통계 기준(기본급, 총액 등)의
 * 확장을 지원합니다.
 */
@Getter
@RequiredArgsConstructor
public enum WageDefinition {
  /**
   * <b>연간 세전 총액 중위임금 (기본 기준선)</b>
   *
   * <ul>
   *   <li><b>포함 항목:</b> 정액급여(기본급) + 특별급여(상여금, 성과급) (초과급여 제외)
   *   <li><b>산출 방식:</b> 통계청(KOSIS) 고용형태별근로실태조사 24개 임금계층 누적분포를 선형 보간하여 도출한 월급여 P50 중위값 × 12개월
   *   <li><b>주요 용도:</b> 사용자가 온보딩 시 입력하는 <b>'세전 총연봉(영끌 연봉)'</b>과의 1:1 연봉 격차(Salary Gap) 비교 기준선
   * </ul>
   */
  ANNUAL_GROSS_MEDIAN("연간 세전 중위임금", "초과급여 제외, 상여 포함", true, false),

  /**
   * <b>연간 정액급여/기본급 중위임금 (보조 기준선)</b>
   *
   * <ul>
   *   <li><b>포함 항목:</b> 순수 기본급(정액급여)만 포함 (상여금·성과급·인센티브 및 초과급여 모두 제외)
   *   <li><b>산출 방식:</b> 통계청 전문가 직군(IT/개발)의 평균 임금 구성비인 정액급여 비중 83.5%({@code BASE_SALARY_RATIO =
   *       0.835}) 계수를 적용하여 역산
   *   <li><b>주요 용도:</b> 성과급/상여금이 없는 기업 재직자나 <b>'순수 계약 기본급'</b> 기준의 시장 비교가 필요한 경우 활용
   * </ul>
   */
  ANNUAL_BASE_MEDIAN("연간 정액급여 중위임금", "초과급여 제외, 상여 제외", false, false);

  /** 화면 노출용 한글 명칭 */
  private final String displayName;

  /** 급여 구성 항목 설명 */
  private final String description;

  /** 상여금/성과급 포함 여부 */
  private final boolean includesBonus;

  /** 초과근로수당 포함 여부 */
  private final boolean includesOvertime;
}
