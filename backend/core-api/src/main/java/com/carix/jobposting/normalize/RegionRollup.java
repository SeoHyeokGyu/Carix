package com.carix.jobposting.normalize;

/**
 * 구 단위 지역을 시도로 올린다. 원천은 `서울 강남구` 처럼 시도와 시군구를 공백으로
 * 이어 주고, 뒤에 `외` 같은 꼬리표가 붙기도 한다. 첫 어절이 시도다.
 */
public final class RegionRollup {

  public static final String UNKNOWN = "UNKNOWN";

  private RegionRollup() {
  }

  public static String toSido(String region) {
    if (region == null || region.isBlank()) {
      return UNKNOWN;
    }
    return region.strip().split("\\s+")[0];
  }
}
