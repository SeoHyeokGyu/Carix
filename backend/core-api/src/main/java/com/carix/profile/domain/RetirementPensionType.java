package com.carix.profile.domain;

/** 퇴직연금 유형. 퇴직금 안내가 이 값으로 갈린다. 미선택(null)은 {@link #UNKNOWN} 과 같게 다룬다. */
public enum RetirementPensionType {
  SEVERANCE_DB,
  DC,
  UNKNOWN
}
