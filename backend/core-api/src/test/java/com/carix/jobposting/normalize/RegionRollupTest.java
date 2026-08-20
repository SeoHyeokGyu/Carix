package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RegionRollupTest {

  @Test
  void takesTheProvincePartOfADetailedRegion() {
    assertEquals("서울", RegionRollup.toSido("서울 강남구"));
    assertEquals("경기", RegionRollup.toSido("경기 성남시"));
    assertEquals("대전", RegionRollup.toSido("대전 유성구"));
  }

  /** 원천이 `서울 강남구 외` 처럼 꼬리표를 붙이는 경우가 있다. 첫 어절만 쓰면 영향이 없다. */
  @Test
  void ignoresTrailingQualifiers() {
    assertEquals("서울", RegionRollup.toSido("서울 강남구 외"));
  }

  @Test
  void keepsSingleWordRegionsAsIs() {
    assertEquals("전남광주", RegionRollup.toSido("전남광주"));
  }

  @Test
  void returnsUnknownForMissingRegion() {
    assertEquals("UNKNOWN", RegionRollup.toSido(null));
    assertEquals("UNKNOWN", RegionRollup.toSido("   "));
  }
}
