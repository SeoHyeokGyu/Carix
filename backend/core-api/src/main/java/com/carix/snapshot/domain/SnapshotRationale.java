package com.carix.snapshot.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code index_snapshot.rationale} 의 JSON 페이로드.
 *
 * <p><b>금액 필드를 두지 않는다.</b> 이 컬럼은 평문이고 연봉은 {@code income_history} 에서 AES-256-GCM 으로 보호된다. 또한 {@link
 * #salaryGapRatio} 와 임금통계 중앙값을 함께 담지 않는다 — $연봉 = 중앙값 / (1 + 격차비율)$ 로 정확히 복원된다 (기술 명세 §1.3). {@link
 * #wageStatVintage} 는 통계 개정판 식별자라 금액이 아니다.
 *
 * @param rulesApplied 적용된 규칙 문구. 임계값 서술이라 금액을 담지 않는다
 * @param salaryGapRatio 소득 격차 비율. 산출 불가면 {@code null}
 * @param excludedSkills 가중합에서 빠진 스킬 이름
 * @param cpiAsOf 사용한 CPI 원천의 기준일
 * @param aggregateAsOf 사용한 시장 집계의 기준일
 * @param wageStatVintage 임금통계 개정판 {@code YYYYMM}
 * @param runId 이 행을 만든 계산 실행 ID. 발급된 {@code data_version} 과 같다
 */
public record SnapshotRationale(
    List<String> rulesApplied,
    BigDecimal salaryGapRatio,
    List<String> excludedSkills,
    LocalDate cpiAsOf,
    LocalDate aggregateAsOf,
    String wageStatVintage,
    String runId) {

  public SnapshotRationale {
    rulesApplied = rulesApplied == null ? List.of() : List.copyOf(rulesApplied);
    excludedSkills = excludedSkills == null ? List.of() : List.copyOf(excludedSkills);
  }
}
