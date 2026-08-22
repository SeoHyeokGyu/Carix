package com.carix.jobposting.normalize;

/** 경력 년수를 동료집단 브래킷으로 바꾼다. 집계와 조회 양쪽에서 쓴다 — 공고는 `experience_min`, 사용자는 입력한 총 경력이 들어온다. */
public final class ExperienceBracket {

  public static final String JUNIOR = "JUNIOR";
  public static final String MID = "MID";
  public static final String SENIOR = "SENIOR";

  private static final int MID_FLOOR = 4;
  private static final int SENIOR_FLOOR = 8;

  private ExperienceBracket() {}

  /** 경력이 없거나 음수면 JUNIOR 다 — 모르는 값을 상위 브래킷으로 올리지 않는다. */
  public static String of(Integer yearsOfExperience) {
    if (yearsOfExperience == null || yearsOfExperience < MID_FLOOR) {
      return JUNIOR;
    }
    return yearsOfExperience < SENIOR_FLOOR ? MID : SENIOR;
  }
}
