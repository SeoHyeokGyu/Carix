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
  private static final long CHEON_IN_MANWON = 1_000L;
  private static final int MAX_DIGITS = 12;
  private static final int GROUPS_PER_AMOUNT = 7;
  // 그룹: 1 억 앞 숫자 / 2 억 뒤 천 숫자 / 3 억 뒤 만 숫자 / 4 숫자 / 5 천 / 6 만 / 7 원
  private static final String AMOUNT =
      "(?:(\\d+)\\s*억(?:\\s*(\\d{1,3})\\s*천(?:\\s*만)?)?(?:\\s*(\\d[\\d,]*)\\s*만)?"
          + "|(\\d[\\d,]*)\\s*(천)?\\s*(만)?)(?:\\s*(원))?";
  private static final Pattern CONTINUES = Pattern.compile("\\s*[\\d천백]");
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
        return toRange(g, text, "월급".equals(k.group(1)), false);
      }
    }
    String stripped = text.strip();
    if (NON_ANNUAL.matcher(stripped).find()) {
      return Optional.empty();
    }
    Matcher g = GROUP.matcher(stripped);
    return g.lookingAt() ? toRange(g, stripped, false, true) : Optional.empty();
  }

  private static Optional<SalaryRange> toRange(
      Matcher g, String text, boolean monthly, boolean needUnit) {
    try {
      return buildRange(g, text, monthly, needUnit);
    } catch (TooLong e) {
      return Optional.empty();
    }
  }

  private static Optional<SalaryRange> buildRange(
      Matcher g, String text, boolean monthly, boolean needUnit) {
    int secondBase = 1 + GROUPS_PER_AMOUNT;
    boolean hasSecond = g.group(secondBase) != null || g.group(secondBase + 3) != null;
    boolean usedEok = g.group(1) != null || (hasSecond && g.group(secondBase) != null);
    // 억 뒤에 금액이 더 이어지는데 읽지 못한 모양(예: 「1억 2백만」)이면 앞부분만 잘라 읽지 않는다.
    if (usedEok && CONTINUES.matcher(text).region(g.end(), text.length()).lookingAt()) {
      return Optional.empty();
    }
    Amount first = amount(g, 1);
    Amount second = hasSecond ? amount(g, secondBase) : null;
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

  /** {@code base} 는 그룹 번호 시작점. */
  private static Amount amount(Matcher g, int base) {
    if (g.group(base) != null) {
      long eok = number(g.group(base));
      long cheon = g.group(base + 1) == null ? 0 : number(g.group(base + 1));
      long man = g.group(base + 2) == null ? 0 : number(g.group(base + 2));
      long won = (eok * EOK_IN_MANWON + cheon * CHEON_IN_MANWON + man) * MANWON;
      return new Amount(won, Unit.RESOLVED);
    }
    boolean cheon = g.group(base + 4) != null;
    boolean man = g.group(base + 5) != null;
    boolean won = g.group(base + 6) != null;
    long value = number(g.group(base + 3)) * (cheon ? CHEON_IN_MANWON : 1L);
    Unit unit = man || (cheon && !won) ? Unit.MANWON : won || cheon ? Unit.WON : null;
    return new Amount(value, unit);
  }

  private static long won(Amount a, Unit fallback) {
    Unit unit = a.unit() != null ? a.unit() : fallback;
    return unit == Unit.MANWON ? a.value() * MANWON : a.value();
  }

  private static long number(String text) {
    String digits = text.replace(",", "");
    if (digits.length() > MAX_DIGITS) {
      throw new TooLong();
    }
    return Long.parseLong(digits);
  }

  /** 숫자가 너무 길어 금액으로 읽을 수 없다. */
  private static final class TooLong extends RuntimeException {
    private static final long serialVersionUID = 1L;

    TooLong() {
      super(null, null, false, false);
    }
  }
}
