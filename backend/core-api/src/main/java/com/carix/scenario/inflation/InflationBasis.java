package com.carix.scenario.inflation;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.math.BigDecimal;

/**
 * 시뮬레이터가 쓰는 두 물가 기준. 율은 % 단위다.
 *
 * @param governmentPercent 한국은행 전망치
 * @param personalPercent 전망치 + 지난 12개월 개인물가-총지수 차이. 비교 불가면 null
 * @param gapPp 개인물가 - 총지수 (%p). 비교 불가면 null
 * @param topGapCategory 격차에 가장 크게 기여한 카테고리. 없으면 null
 * @param similar 격차의 절댓값이 {@link InflationAssumptions#INFLATION_GAP_THRESHOLD_PP} 미만
 * @param asOf 전망 기준월 {@code yyyy-MM}
 * @param source 전망 출처
 */
public record InflationBasis(
    BigDecimal governmentPercent,
    BigDecimal personalPercent,
    BigDecimal gapPp,
    ExpenseCategoryEnum topGapCategory,
    boolean similar,
    String asOf,
    String source) {}
