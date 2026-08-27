package com.carix.index;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 시장가치 지수 산출 결과 (Member B → Member C).
 *
 * @param baseYearMonth 산출 대상 월. {@code YYYYMM} 6자다
 * @param value 원천별 지수를 표본수 가중 결합한 값. 계산 불가면 {@code null} 이다
 * @param status 값이 없는 이유. 계산 가능 스킬이 0개면 {@link IndexStatus#INSUFFICIENT_SAMPLE} 이다 (§4.2)
 * @param sources 원천별 완화 단계와 표본. 완화가 원천마다 독립이라 목록이다 (도메인 정의서 §2.3)
 * @param excludedSkills 가중합에서 빠진 스킬과 사유. 남은 스킬로 가중치를 재정규화한 뒤의 목록이다
 * @param wageBaseline 연봉 격차 기준선. 기준선을 못 구하면 {@code null}
 * @param aggregateAsOf 사용한 시장 집계의 기준일. {@code index_snapshot.rationale} 의 `원천 기준일` 이 된다
 */
public record MviResult(
    String baseYearMonth,
    BigDecimal value,
    IndexStatus status,
    List<SourceCoverage> sources,
    List<ExcludedSkill> excludedSkills,
    WageBaseline wageBaseline,
    LocalDate aggregateAsOf) {

  public MviResult {
    sources = sources == null ? List.of() : List.copyOf(sources);
    excludedSkills = excludedSkills == null ? List.of() : List.copyOf(excludedSkills);
  }

  /** 입력이 없거나 산출기가 준비되지 않은 상태. */
  public static MviResult notAvailable(String baseYearMonth) {
    return new MviResult(
        baseYearMonth, null, IndexStatus.NOT_AVAILABLE, List.of(), List.of(), null, null);
  }
}
