package com.carix.snapshot;

/**
 * 결정표 버전. 비교는 사전순이 아니라 {@code major.minor.patch} 숫자 세그먼트 비교다 — {@code 1.10.0 > 1.9.0} (기술 명세
 * §1.3). 문자열 정렬에 맡기면 규칙표가 배포될수록 최신판을 놓친다.
 */
public record RuleVersion(int major, int minor, int patch) implements Comparable<RuleVersion> {

  public static RuleVersion of(String value) {
    String[] segments = value.split("\\.");
    if (segments.length != 3) {
      throw new IllegalArgumentException("rule_version 은 major.minor.patch 세 세그먼트다: " + value);
    }
    try {
      return new RuleVersion(
          Integer.parseInt(segments[0]),
          Integer.parseInt(segments[1]),
          Integer.parseInt(segments[2]));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("rule_version 의 각 세그먼트는 정수다: " + value, e);
    }
  }

  public String value() {
    return major + "." + minor + "." + patch;
  }

  @Override
  public int compareTo(RuleVersion other) {
    int byMajor = Integer.compare(major, other.major);
    if (byMajor != 0) {
      return byMajor;
    }
    int byMinor = Integer.compare(minor, other.minor);
    if (byMinor != 0) {
      return byMinor;
    }
    return Integer.compare(patch, other.patch);
  }
}
