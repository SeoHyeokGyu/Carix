package com.carix.snapshot;

/** 결정 신호. 값은 {@code core.index_snapshot.decision_signal} 에 그대로 들어간다 (기술 명세 §1.2 DDL 주석). */
public enum DecisionSignalCode {
  /** 이직 탐색 (규칙 2). */
  SEEK_JOB,
  /** 협상 검토 (규칙 3). */
  NEGOTIATE,
  /** 스킬 보강 우선 (규칙 4). */
  UPSKILL,
  /** 현 상태 유지 (규칙 5 및 기본행). */
  STAY,
  /** 데이터 부족 (우선순위 1). */
  INSUFFICIENT_DATA
}
