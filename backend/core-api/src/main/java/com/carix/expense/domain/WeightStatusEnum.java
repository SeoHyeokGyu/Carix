package com.carix.expense.domain;

/**
 * 카테고리별 지출 금액의 출처 상태.
 *
 * <p>{@code USER_DEFINED}: 사용자가 직접 입력. {@code DEFAULT_CPI}: 미입력 카테고리를 통계청 가계동향조사 평균으로 보완. {@code
 * PROPAGATED}: 과거 수정이 미래 월로 전파됨 (전파 로직은 추후 구현 — 지금은 값만 정의). 전체 요약 상태({@code PARTIALLY_ESTIMATED}
 * 포함)는 저장하지 않고 {@link com.carix.expense.service.ExpenseService}가 매번 계산한다.
 */
public enum WeightStatusEnum {
  USER_DEFINED,
  DEFAULT_CPI,
  PROPAGATED
}
