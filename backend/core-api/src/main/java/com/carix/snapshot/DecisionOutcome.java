package com.carix.snapshot;

import java.math.BigDecimal;
import java.util.List;

/**
 * 결정 판정 결과.
 *
 * @param code 결정 신호
 * @param rulesApplied 화면과 {@code rationale} 에 그대로 쓰는 규칙 문구. 임계값 서술이라 금액을 담지 않는다
 * @param salaryGapRatio 소득 격차 비율. 산출 불가면 {@code null}. **임금통계 중앙값과 함께 저장하지 않는다** — 둘이 있으면 연봉이
 *     복원된다 (기술 명세 §1.3)
 */
public record DecisionOutcome(
    DecisionSignalCode code, List<String> rulesApplied, BigDecimal salaryGapRatio) {

  public DecisionOutcome {
    rulesApplied = rulesApplied == null ? List.of() : List.copyOf(rulesApplied);
  }
}
