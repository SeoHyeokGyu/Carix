package com.carix.snapshot;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * {@code recalculateSnapshot(userId, fromYm)} 이 받는 {@code YYYYMM} 문자열을 만든다.
 *
 * <p>네 도메인이 각자 포맷하면 한 곳만 틀려도 그 경로의 재계산이 조용히 엉뚱한 달부터 돈다. 기준 시간대는 {@link
 * SnapshotRecalculationService} 의 현재월 계산과 같은 UTC 다 — 다르면 월말에 두 값이 한 달 어긋난다.
 */
public final class YearMonths {

  private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

  private YearMonths() {}

  /** 날짜가 속한 달. 소득의 유효 시작일처럼 값 자체에 시점이 있을 때 쓴다. */
  public static String of(LocalDate date) {
    return YYYYMM.format(date);
  }

  /** 변경이 일어난 달. 스킬·프로필처럼 값에 유효월이 없을 때 쓴다. */
  public static String current() {
    return YYYYMM.format(YearMonth.now(ZoneOffset.UTC));
  }

  /** 두 달 중 이른 쪽. 유효월을 앞으로 당기는 수정에서 옛 달이 갱신되지 않고 남는 것을 막는다. */
  public static String earlier(String left, String right) {
    return left.compareTo(right) <= 0 ? left : right;
  }
}
