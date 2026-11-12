package com.carix.scenario.projection;

import java.time.LocalDate;

/**
 * 투영에 퇴직금을 넣기 위한 조건. 「m 개월째에 나가면」의 마지막 근무일은 {@code today.plusMonths(m)} 이다.
 *
 * @param hireDate 지금 회사 입사일
 * @param today 투영 기준일
 */
public record SeveranceTerms(LocalDate hireDate, LocalDate today) {}
