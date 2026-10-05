package com.carix.pipeline.health;

/** 점검 판정. 판정할 데이터가 없으면 정상이 아니라 {@link #UNKNOWN} 이다 — 숫자를 지어내지 않는다는 제품 원칙. */
public enum HealthVerdict {
  OK,
  WARN,
  ERROR,
  UNKNOWN
}
