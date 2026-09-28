package com.carix.index.dto;

import com.carix.index.enums.IndexStatus;
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
 * @param zeroDemandSkills 최근 3개월 관측이 없어 <b>수요 지수 0 으로 가중합에 들어간</b> 스킬. 제외가 아니라 0 반영이라 {@link
 *     #excludedSkills} 와 분리한다 (기능 명세 §4.1) — 합치면 {@code index_snapshot.rationale} 의 "가중합에서 빠진 스킬" 이
 *     거짓이 된다. 가중치는 그대로 배분받으므로 다른 스킬의 비중을 끌어내린다
 * @param wageBaseline 연봉 격차 기준선. 기준선을 못 구하면 {@code null}
 * @param aggregateAsOf 사용한 시장 집계의 기준일. {@code index_snapshot.rationale} 의 `원천 기준일` 이 된다
 */
public record MviResult(
    String baseYearMonth,
    BigDecimal value,
    IndexStatus status,
    List<SourceCoverage> sources,
    List<ExcludedSkill> excludedSkills,
    List<ExcludedSkill> zeroDemandSkills,
    List<SkillContribution> skillContributions,
    WageBaseline wageBaseline,
    LocalDate aggregateAsOf) {

  public MviResult {
    IndexValueContract.MVI.requireArgument(baseYearMonth, value, status);
    sources = sources == null ? List.of() : List.copyOf(sources);
    excludedSkills = excludedSkills == null ? List.of() : List.copyOf(excludedSkills);
    zeroDemandSkills = zeroDemandSkills == null ? List.of() : List.copyOf(zeroDemandSkills);
    skillContributions = skillContributions == null ? List.of() : List.copyOf(skillContributions);
  }

  /** 입력이 없거나 산출기가 준비되지 않은 상태. */
  public static MviResult notAvailable(String baseYearMonth) {
    return new MviResult(
        baseYearMonth,
        null,
        IndexStatus.NOT_AVAILABLE,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }
}
