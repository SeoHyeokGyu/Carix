package com.carix.jobposting.normalize;

/** 경력 년수를 동료집단 브래킷으로 바꾼다. 집계와 조회 양쪽에서 쓴다 — 공고는 `experience_min`, 사용자는 입력한 총 경력이 들어온다. */
public final class ExperienceBracket {

  public static final String ENTRY = "ENTRY";
  public static final String JUNIOR = "JUNIOR";
  public static final String MID = "MID";
  public static final String SENIOR = "SENIOR";
  public static final String LEAD = "LEAD";
  public static final String ALL = "ALL";

  private static final int JUNIOR_FLOOR = 2;
  private static final int MID_FLOOR = 4;
  private static final int SENIOR_FLOOR = 8;
  private static final int LEAD_FLOOR = 12;

  private ExperienceBracket() {}

  /** 경력이 없거나 음수면 JUNIOR 다 — 모르는 값을 상위 브래킷으로 올리지 않는다 (기존 3구간 호환). */
  public static String of(Integer yearsOfExperience) {
    if (yearsOfExperience == null || yearsOfExperience < MID_FLOOR) {
      return JUNIOR;
    }
    return yearsOfExperience < SENIOR_FLOOR ? MID : SENIOR;
  }

  /**
   * 경력 년수를 6구간 브래킷(ENTRY, JUNIOR, MID, SENIOR, LEAD)으로 변환한다. KOSIS 공식 임금통계
   * 기준선(wage_stat_observation) 및 경력 사다리(careerLadder)에서 사용된다.
   */
  public static String of6(Integer yearsOfExperience) {
    if (yearsOfExperience == null || yearsOfExperience < JUNIOR_FLOOR) {
      return ENTRY;
    }
    if (yearsOfExperience < MID_FLOOR) {
      return JUNIOR;
    }
    if (yearsOfExperience < SENIOR_FLOOR) {
      return MID;
    }
    if (yearsOfExperience < LEAD_FLOOR) {
      return SENIOR;
    }
    return LEAD;
  }

  /** 6구간 브래킷을 채용공고 동료집단 완화용 3구간 브래킷으로 롤업한다. ENTRY -> JUNIOR, LEAD -> SENIOR. */
  public static String to3Tier(String bracket) {
    if (bracket == null) {
      return JUNIOR;
    }
    return switch (bracket) {
      case ENTRY, JUNIOR -> JUNIOR;
      case MID -> MID;
      case SENIOR, LEAD -> SENIOR;
      default -> bracket;
    };
  }
}
