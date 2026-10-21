package com.carix.index.inflation;

import com.carix.expense.domain.ExpenseCategoryEnum;
import java.math.BigDecimal;

/**
 * 지난 12개월 개인물가 상승률과 전체 CPI(총지수) 상승률의 비교. 율은 % 단위, 소수 둘째 자리다 (2.50 = 2.50%).
 *
 * @param cpiMonth 사용한 CPI 기준월 {@code YYYYMM}
 * @param gapPp 개인물가 - 총지수, 표시되는 두 값의 차이 (%p)
 * @param topGapCategory 평균 소비구조 대비 가중치 차이가 격차에 가장 크게 기여한 카테고리
 * @param topGapContributionPp 그 카테고리의 기여 (%p). 부호가 있다
 */
public record InflationComparison(
    String cpiMonth,
    BigDecimal personalRate,
    BigDecimal headlineRate,
    BigDecimal gapPp,
    ExpenseCategoryEnum topGapCategory,
    BigDecimal topGapContributionPp) {}
