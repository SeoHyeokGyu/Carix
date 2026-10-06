package com.carix.jobposting.normalize;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고 본문 급여 문구에서 공개 연봉(연간 원)을 읽는다. 「연봉 4,512만원」, 「연봉 4,000~6,000만원」, 「연봉 5,000만원 이상(면접 후 결정)」, 「월급
 * 500~600만원」, 「연봉:4,100 만원」, 「연봉 1억 2,000만원」, 「연봉 45,000,000원」.
 *
 * <p>한쪽 끝만 있는 「이상」·「이하」는 그 값을 min=max 로 둔다 — 소비처가 min·max 둘 다 있어야 연봉으로 센다. 시급·일급·주급은 연봉 비교 대상이 아니라
 * 공개로 보지 않는다.
 */
public final class SalaryTextParser {

  private static final long MANWON = 10_000L;
  private static final long EOK_IN_MANWON = 10_000L;
  private static final long MIN_ANNUAL = 10_000_000L;
  private static final long MAX_ANNUAL = 1_000_000_000L;
  private static final int MONTHS = 12;
  private static final String AMOUNT =
      "(?:(\\d+)\\s*억(?:\\s*(\\d[\\d,]*)\\s*만)?|(\\d[\\d,]*)\\s*(만)?)\\s*(원)?";
  private static final Pattern GROUP =
      Pattern.compile(AMOUNT + "(?:\\s*[~∼\\-–]\\s*" + AMOUNT + ")?");
  private static final Pattern KEYWORD = Pattern.compile("(연봉|월급)\\s*:?\\s*");
  private static final Pattern NON_ANNUAL = Pattern.compile("시급|일급|주급");

  private SalaryTextParser() {}

  /** 금액 한 덩이. {@code unit} 이 null 이면 단위 표기가 없다. */
  private record Amount(long value, Unit unit) {}

  private enum Unit {
    WON,
    MANWON,
    RESOLVED
  }

  public static Optional<SalaryRange> parseAnnual(String text) {
    if (text == null || text.isBlank()) {
      return Optional.empty();
    }
    Matcher k = KEYWORD.matcher(text);
    while (k.find()) {
      Matcher g = GROUP.matcher(text).region(k.end(), text.length());
      if (g.lookingAt()) {
        return toRange(g, "월급".equals(k.group(1)), false);
      }
    }
    String stripped = text.strip();
    if (NON_ANNUAL.matcher(stripped).find()) {
      return Optional.empty();
    }
    Matcher g = GROUP.matcher(stripped);
    return g.lookingAt() ? toRange(g, false, true) : Optional.empty();
  }

  private static Optional<SalaryRange> toRange(Matcher g, boolean monthly, boolean needUnit) {
    Amount first = amount(g, 1);
    Amount second = g.group(6) != null || g.group(8) != null ? amount(g, 6) : null;
    Unit fallback = first.unit() != null ? first.unit() : second == null ? null : second.unit();
    if (needUnit && fallback == null) {
      return Optional.empty();
    }
    long min = won(first, fallback);
    long max = second == null ? min : won(second, fallback);
    if (monthly) {
      min *= MONTHS;
      max *= MONTHS;
    }
    if (min > max || min < MIN_ANNUAL || max > MAX_ANNUAL) {
      return Optional.empty();
    }
    return Optional.of(new SalaryRange(min, max));
  }

  /** {@code base} 는 그룹 번호 시작점 — 억 / 억 뒤 만 / 숫자 / 만 / 원 순서로 5개 그룹이 이어진다. */
  private static Amount amount(Matcher g, int base) {
    if (g.group(base) != null) {
      long man = g.group(base + 1) == null ? 0 : number(g.group(base + 1));
      long won = (Long.parseLong(g.group(base)) * EOK_IN_MANWON + man) * MANWON;
      return new Amount(won, Unit.RESOLVED);
    }
    Unit unit =
        g.group(base + 3) != null ? Unit.MANWON : g.group(base + 4) != null ? Unit.WON : null;
    return new Amount(number(g.group(base + 2)), unit);
  }

  private static long won(Amount a, Unit fallback) {
    Unit unit = a.unit() != null ? a.unit() : fallback;
    return unit == Unit.MANWON ? a.value() * MANWON : a.value();
  }

  private static long number(String digits) {
    return Long.parseLong(digits.replace(",", ""));
  }
}
