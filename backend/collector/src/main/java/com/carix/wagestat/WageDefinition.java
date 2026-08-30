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
   * 연간 세전 중위임금 (초과급여 제외, 정액급여 및 상여금 포함).
   *
   * <p>통계청 24개 임금계층 누적분포를 선형 보간하여 P50 중위값을 도출한 기본 기준선.
   */
  ANNUAL_GROSS_MEDIAN("연간 세전 중위임금", "초과급여 제외, 상여 포함", true, false),

  /**
   * 연간 기본급/정액급여 중위임금 (상여금 및 초과급여 모두 제외).
   *
   * <p>추후 순수 기본급 비교용 기준선 확장 시 활용.
   */
  ANNUAL_BASE_MEDIAN("연간 정액급여 중위임금", "초과급여 제외, 상여 제외", false, false);

  private final String displayName;
  private final String description;
  private final boolean includesBonus;
  private final boolean includesOvertime;
}
