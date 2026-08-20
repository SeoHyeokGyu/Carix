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
}
