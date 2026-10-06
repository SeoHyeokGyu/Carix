package com.carix.jobposting.normalize;

/** 공고 한 건의 급여 판정 결과. {@code min/max} 는 공개 연봉만, {@code band*} 는 비공개 검색 구간. 단위는 연간 원. */
public record PostingSalary(
    SalaryDisclosure disclosure, Long min, Long max, Long bandMin, Long bandMax) {

  public static PostingSalary publicRange(long min, long max) {
    return new PostingSalary(SalaryDisclosure.PUBLIC, min, max, null, null);
  }

  public static PostingSalary band(SalaryRange band) {
    return new PostingSalary(SalaryDisclosure.BAND, null, null, band.min(), band.max());
  }

  public static PostingSalary none() {
    return new PostingSalary(SalaryDisclosure.NONE, null, null, null, null);
  }

  public static PostingSalary unknown() {
    return new PostingSalary(SalaryDisclosure.UNKNOWN, null, null, null, null);
  }
}
