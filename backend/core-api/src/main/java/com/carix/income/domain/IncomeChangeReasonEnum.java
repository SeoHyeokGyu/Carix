package com.carix.income.domain;

/**
 * 소득 변경 사유.
 *
 * <p>{@code RAISE}·{@code JOB_CHANGE} 만 {@code core.income_history.change_reason} 컬럼에 저장한다. {@code
 * INITIAL} 은 <b>응답 전용</b>이다 — 그 사용자의 가장 이른 유효일 행에 {@link com.carix.income.service.IncomeService} 가
 * 읽기 시점에 얹는다. 저장하면 더 이른 과거 이력이 나중에 들어올 때 행 사이를 옮겨 다녀야 하고, 그 {@code UPDATE} 가 이미 그 행에 있던 사용자 선택값을
 * 덮는다. DB 의 {@code CHECK} 제약이 같은 규칙을 한 번 더 막는다 ({@code deploy/postgres/schema/02_user_domain.sql}).
 */
public enum IncomeChangeReasonEnum {
  INITIAL,
  RAISE,
  JOB_CHANGE
}
