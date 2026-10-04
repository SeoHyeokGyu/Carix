package com.carix.jobposting.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthIdRangeFinderTest {

  /** ID 1000~1999 를 하루 10개씩 2026-01-01 부터 배정하고, 일부 ID 는 삭제(빈 페이지)로 둔다. */
  private static Map<Long, LocalDate> pages() {
    Map<Long, LocalDate> pages = new HashMap<>();
    for (long id = 1000; id < 2000; id++) {
      if (id % 7 == 0) {
        continue; // 삭제된 공고
      }
      pages.put(id, LocalDate.of(2026, 1, 1).plusDays((id - 1000) / 10));
    }
    return pages;
  }

  @Test
  @DisplayName("게시일이 기준일 이상인 첫 ID 를 이분 탐색으로 찾는다 — 빈 ID 는 옆으로 비켜 본다")
  void findsFirstIdOnOrAfterDate() {
    Map<Long, LocalDate> pages = pages();
    MonthIdRangeFinder finder =
        new MonthIdRangeFinder(id -> Optional.ofNullable(pages.get(id)), 20);

    long first = finder.findFirstIdOnOrAfter(LocalDate.of(2026, 2, 1), 1000, 2000);

    // 2026-02-01 은 31일째 → ID 1310 부터
    assertThat(first).isBetween(1309L, 1311L);
    assertThat(pages.get(first)).isAfterOrEqualTo(LocalDate.of(2026, 2, 1));
  }

  @Test
  @DisplayName("범위 전체가 기준일 이후면 하한을, 이전이면 상한을 돌려준다")
  void clampsToBounds() {
    Map<Long, LocalDate> pages = pages();
    MonthIdRangeFinder finder =
        new MonthIdRangeFinder(id -> Optional.ofNullable(pages.get(id)), 20);

    assertThat(finder.findFirstIdOnOrAfter(LocalDate.of(2025, 1, 1), 1000, 2000)).isEqualTo(1000);
    assertThat(finder.findFirstIdOnOrAfter(LocalDate.of(2027, 1, 1), 1000, 2000)).isEqualTo(2000);
  }
}
