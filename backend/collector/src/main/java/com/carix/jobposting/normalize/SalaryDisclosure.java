package com.carix.jobposting.normalize;

/** 공고 연봉의 공개 구분. 설계 §4.1. */
public enum SalaryDisclosure {
  /** 공고 본문에 금액이 적혀 있다. */
  PUBLIC,
  /** 본문은 회사내규·협의이고, 회사가 검색용으로 넣은 비공개 구간만 있다. */
  BAND,
  /** 공개 금액도 비공개 구간도 없다. */
  NONE,
  /** 아직 판정하지 않았거나 원천을 다시 받지 못했다. */
  UNKNOWN
}
