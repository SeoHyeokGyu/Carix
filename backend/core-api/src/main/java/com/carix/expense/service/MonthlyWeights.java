package com.carix.expense.service;

import com.carix.expense.domain.ExpenseCategoryEnum;
import com.carix.expense.domain.WeightStatusEnum;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 한 달의 소비 가중치(6개 카테고리, 합 1)와 카테고리별 출처. 구매력 지수가 그 달 개인물가 변화율을 가중하는 데 쓴다.
 *
 * <p>금액 기준이 없는 달(기본값 전체 대체)은 기본 가중치를 그대로 담는다 — 엔진은 비중만 쓴다.
 */
public record MonthlyWeights(
    Map<ExpenseCategoryEnum, BigDecimal> weights,
    Map<ExpenseCategoryEnum, WeightStatusEnum> statuses) {

  public MonthlyWeights {
    weights = Map.copyOf(weights);
    statuses = Map.copyOf(statuses);
  }
}
