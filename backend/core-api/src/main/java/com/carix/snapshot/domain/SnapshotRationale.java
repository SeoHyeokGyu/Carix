package com.carix.snapshot.domain;

import com.carix.index.dto.SkillContribution;
import com.carix.index.dto.SourceCoverage;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code index_snapshot.rationale} 의 JSON 페이로드.
 *
 * <p><b>금액 필드를 두지 않는다.</b> 이 컬럼은 평문이고 연봉은 {@code income_history} 에서 AES-256-GCM 으로 보호된다. 격차도 비율이
 * 아니라 구간으로 담는다 — 비율과 임금통계 중앙값이 함께 있으면 $연봉 = 중앙값 / (1 + 격차비율)$ 로 정확히 복원된다 (기술 명세 §1.3, 기술 명세 §1.3).
 * {@link #wageStatVintage} 는 통계 개정판 식별자라 금액이 아니다.
 *
 * @param rulesApplied 적용된 규칙 문구. 임계값 서술이라 금액을 담지 않는다
 * @param salaryGapBucket 소득 격차 구간({@link com.carix.snapshot.SalaryGapBucket}). 비율을 그대로 담으면 중앙값과 조인해
 *     연봉이 원 단위로 복원되므로 구간만 남긴다 (기술 명세 §1.3). 산출 불가면 {@code null}
 * @param sourceCoverages 원천별 완화 단계·표본 수·결측률. {@code MviResult} 에만 있고 저장되지 않으면 "표본이 부족해 조건을 넓혔다"는
 *     이력이 사라진다 (기술 명세 §1.3)
 * @param skillContributions 스킬별 가중치 × 수요 지수. 빠진 스킬의 이름만으로는 어떤 스킬이 지수를 끌어올렸는지 시간축으로 볼 수 없다 (기술 명세
 *     §1.3)
 * @param excludedSkills 가중합에서 빠진 스킬 이름
 * @param cpiAsOf 사용한 CPI 원천의 기준일
 * @param aggregateAsOf 사용한 시장 집계의 기준일
 * @param wageStatVintage 임금통계 개정판 {@code YYYYMM}
 * @param runId 이 행을 만든 계산 실행 ID. 발급된 {@code data_version} 과 같다
 */
public record SnapshotRationale(
    List<String> rulesApplied,
    String salaryGapBucket,
    List<SourceCoverage> sourceCoverages,
    List<SkillContribution> skillContributions,
    List<String> excludedSkills,
    LocalDate cpiAsOf,
    LocalDate aggregateAsOf,
    String wageStatVintage,
    String runId) {

  public SnapshotRationale {
    rulesApplied = rulesApplied == null ? List.of() : List.copyOf(rulesApplied);
    excludedSkills = excludedSkills == null ? List.of() : List.copyOf(excludedSkills);
    sourceCoverages = sourceCoverages == null ? List.of() : List.copyOf(sourceCoverages);
    skillContributions = skillContributions == null ? List.of() : List.copyOf(skillContributions);
  }
}
