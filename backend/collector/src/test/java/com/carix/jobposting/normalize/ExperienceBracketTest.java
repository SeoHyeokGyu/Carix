package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExperienceBracketTest {

  @Test
  void classifiesByYearBoundaries() {
    assertEquals("JUNIOR", ExperienceBracket.of(0));
    assertEquals("JUNIOR", ExperienceBracket.of(3));
    assertEquals("MID", ExperienceBracket.of(4));
    assertEquals("MID", ExperienceBracket.of(7));
    assertEquals("SENIOR", ExperienceBracket.of(8));
    assertEquals("SENIOR", ExperienceBracket.of(40));
  }

  /** 원천이 경력을 안 주는 공고가 있다. 신입으로 보는 편이 과대평가보다 안전하다. */
  @Test
  void treatsMissingExperienceAsJunior() {
    assertEquals("JUNIOR", ExperienceBracket.of(null));
  }

  @Test
  void treatsNegativeExperienceAsJunior() {
    assertEquals("JUNIOR", ExperienceBracket.of(-1));
  }

  @Test
  void classifiesBy6TierYearBoundaries() {
    assertEquals("ENTRY", ExperienceBracket.of6(0));
    assertEquals("ENTRY", ExperienceBracket.of6(1));
    assertEquals("JUNIOR", ExperienceBracket.of6(2));
    assertEquals("JUNIOR", ExperienceBracket.of6(3));
    assertEquals("MID", ExperienceBracket.of6(4));
    assertEquals("MID", ExperienceBracket.of6(7));
    assertEquals("SENIOR", ExperienceBracket.of6(8));
    assertEquals("SENIOR", ExperienceBracket.of6(11));
    assertEquals("LEAD", ExperienceBracket.of6(12));
    assertEquals("LEAD", ExperienceBracket.of6(30));
    assertEquals("ENTRY", ExperienceBracket.of6(null));
    assertEquals("ENTRY", ExperienceBracket.of6(-1));
  }

  @Test
  void rollsUpTo3TierCorrectly() {
    assertEquals("JUNIOR", ExperienceBracket.to3Tier("ENTRY"));
    assertEquals("JUNIOR", ExperienceBracket.to3Tier("JUNIOR"));
    assertEquals("MID", ExperienceBracket.to3Tier("MID"));
    assertEquals("SENIOR", ExperienceBracket.to3Tier("SENIOR"));
    assertEquals("SENIOR", ExperienceBracket.to3Tier("LEAD"));
    assertEquals("JUNIOR", ExperienceBracket.to3Tier(null));
    assertEquals("UNKNOWN", ExperienceBracket.to3Tier("UNKNOWN"));
  }
}
