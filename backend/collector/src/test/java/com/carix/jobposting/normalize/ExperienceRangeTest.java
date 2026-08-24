package com.carix.jobposting.normalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ExperienceRangeTest {

  /**
   * 이것이 고치려는 결함이다. 사람인은 `경력 3~10년` 처럼 범위를 준다(실측 397건 중 135건). 종전 파서는 `(\d+)년` 으로 년 바로 앞 숫자만 잡아 상한인
   * 10 을 `experience_min` 에 넣었다 — 3년차도 지원 가능한 공고가 시니어 공고로 분류됐다.
   */
  @Test
  void takesBothEndsOfARange() {
    assertEquals(new ExperienceRange(3, 10), ExperienceRange.parse("경력 3~10년"));
    assertEquals(new ExperienceRange(2, 15), ExperienceRange.parse("경력 2~15년"));
    assertEquals(new ExperienceRange(1, 3), ExperienceRange.parse("경력 1~3년"));
  }

  /** 잡코리아와 사람인 모두 쓰는 표기다. 상한이 없다. */
  @Test
  void readsAnOpenEndedMinimum() {
    assertEquals(new ExperienceRange(3, null), ExperienceRange.parse("경력3년↑"));
    assertEquals(new ExperienceRange(10, null), ExperienceRange.parse("경력10년↑"));
  }

  /**
   * 화살표 방향이 의미를 뒤집는다. `경력6년↓` 는 6년 이하라는 뜻이라 6 은 상한이다 — 하한으로 읽으면 주니어 공고가 시니어로 간다. 실측 797건 중 1건뿐이지만
   * 방향을 반대로 읽는 것이라 무시할 수 없다.
   */
  @Test
  void readsADownwardArrowAsAnUpperBound() {
    assertEquals(new ExperienceRange(null, 6), ExperienceRange.parse("경력6년↓"));
  }

  /** 신입만 받는 공고는 상한도 0 이다. */
  @Test
  void pinsNewGraduateOnlyToZero() {
    assertEquals(new ExperienceRange(0, 0), ExperienceRange.parse("신입"));
  }

  /** 신입을 받으면 연차 하한은 0 이다. 뒤에 붙은 경력 연차는 상한이 아니라 우대 조건이다. */
  @Test
  void treatsNewGraduateAcceptanceAsNoFloor() {
    assertEquals(new ExperienceRange(0, null), ExperienceRange.parse("신입·경력"));
    assertEquals(new ExperienceRange(0, null), ExperienceRange.parse("신입·경력3년↑"));
  }

  /** 연차 조건이 없다는 뜻이므로 하한은 0 이다. */
  @Test
  void treatsAnyExperienceAsNoFloor() {
    assertEquals(new ExperienceRange(0, null), ExperienceRange.parse("경력무관"));
  }

  /**
   * `경력` 단독은 경력자를 원한다는 것만 알려주고 연차를 말하지 않는다. 0 으로 두면 "신입을 받는다"는 거짓 진술이 되므로 모름(null)으로 둔다 — {@link
   * ExperienceBracket} 이 null 을 JUNIOR 로 보내므로 브래킷은 달라지지 않는다.
   */
  @Test
  void leavesBareExperienceUnknown() {
    assertEquals(new ExperienceRange(null, null), ExperienceRange.parse("경력"));
  }

  @Test
  void leavesMissingInputUnknown() {
    assertEquals(new ExperienceRange(null, null), ExperienceRange.parse(null));
    assertEquals(new ExperienceRange(null, null), ExperienceRange.parse("   "));
    assertNull(ExperienceRange.parse("정규직").min());
  }
}
