package com.carix.index;

import com.carix.index.dto.CurrentSalary;
import java.util.Optional;
import java.util.UUID;

/**
 * 결정 신호의 소득 격차 비율(기술 명세 §2.1)이 분모로 쓰는 현재 연봉을 넘긴다.
 *
 * <p>원천은 {@code core.income_history.gross_salary_encrypted} 로 Member A 소유의 암호화 컬럼이다. 값을 {@link
 * PpiResult} 에 실지 않고 계약을 따로 두는 이유는 두 가지다 — {@code PpiResult} 는 스냅샷 {@code rationale} 경로까지 흘러가므로 평문
 * 금액이 남고, 복호화 키 접근이 소득 도메인 밖으로 새면 감사 경계가 둘로 갈라진다.
 *
 * <p>연봉 이력이 없으면 {@link Optional#empty()} 다. 0 을 돌려주지 않는다 — 격차 비율의 분모라 0 은 계산을 깨뜨린다.
 */
public interface CurrentSalaryProvider {

  /**
   * 기준월 시점의 세전 연봉을 조회한다.
   *
   * <p>연봉 이력이 없으면 {@link Optional#empty()} 다. 0 을 돌려주지 않는다 — 격차 비율의 분모라 0 은 계산을 깨뜨린다.
   *
   * @param userId 대상 사용자
   * @param baseYearMonth 기준년월. {@code YYYYMM} 6자
   * @return 현재 연봉. 이력이 없으면 empty
   */
  Optional<CurrentSalary> currentGrossSalary(UUID userId, String baseYearMonth);
}
