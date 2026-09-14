package com.carix.snapshot;

/**
 * 결정 신호. 값은 {@code core.index_snapshot.decision_signal} 에 그대로 들어간다 (기술 명세 §1.2 DDL 주석).
 *
 * <p>화면에 나가는 한글 문구도 여기 있다. 서비스마다 따로 {@code switch} 를 두면 문구가 갈린다 — 캐노니컬 표기는 MVP 요구사항 §193-196 과 데이터
 * 아키텍처 §132-133 이다.
 */
public enum DecisionSignalCode {
  /** 이직 탐색 (규칙 2). */
  SEEK_JOB("이직 탐색"),
  /** 협상 검토 (규칙 3). */
  NEGOTIATE("협상 검토"),
  /** 스킬 보강 우선 (규칙 4). */
  UPSKILL("스킬 보강 우선"),
  /** 현 상태 유지 (규칙 5 및 기본행). */
  STAY("현 상태 유지"),
  /** 데이터 부족 (우선순위 1). */
  INSUFFICIENT_DATA("데이터 부족");

  private final String label;

  DecisionSignalCode(String label) {
    this.label = label;
  }

  /** 화면에 그대로 쓰는 한글 문구. */
  public String label() {
    return label;
  }
}
