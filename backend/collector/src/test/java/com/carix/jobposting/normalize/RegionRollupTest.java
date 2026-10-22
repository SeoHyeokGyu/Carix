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

  /** 시군구 없이 시도 전체를 가리키는 표기다(실측: `부산전체` 2건, `경기전체` 1건). */
  @Test
  void stripsTheWholeProvinceSuffix() {
    assertEquals("부산", RegionRollup.toSido("부산전체"));
    assertEquals("경기", RegionRollup.toSido("경기전체"));
  }

  @Test
  void normalizesFullProvinceNames() {
    assertEquals("세종", RegionRollup.toSido("세종특별자치시"));
    assertEquals("충북", RegionRollup.toSido("충청북도 청주시"));
  }

  /** 전남에는 자치구가 없다. 구는 광주광역시, 시·군은 전남이다. */
  @Test
  void splitsTheCombinedJeonnamGwangjuLabel() {
    assertEquals("광주", RegionRollup.toSido("전남광주 북구"));
    assertEquals("광주", RegionRollup.toSido("전남광주 광산구"));
    assertEquals("광주", RegionRollup.toSido("전남광주 북구 외"));
    assertEquals("전남", RegionRollup.toSido("전남광주 나주시"));
    assertEquals("전남", RegionRollup.toSido("전남광주 목포시 외"));
  }

  /** 시군구가 없으면 전남인지 광주인지 가릴 근거가 없다. */
  @Test
  void returnsUnknownForTheBareCombinedLabel() {
    assertEquals("UNKNOWN", RegionRollup.toSido("전남광주"));
  }

  /** 지역 컬럼에 고용형태(`정규직`)나 해외(`일본`)가 섞여 들어온 실측 사례다. */
  @Test
  void returnsUnknownForValuesThatAreNotDomesticProvinces() {
    assertEquals("UNKNOWN", RegionRollup.toSido("정규직"));
    assertEquals("UNKNOWN", RegionRollup.toSido("일본"));
  }
}
