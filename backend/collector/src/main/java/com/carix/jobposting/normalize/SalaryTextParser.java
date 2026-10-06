package com.carix.jobposting.normalize;

import java.util.ArrayList;
import java.util.List;
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
  private static final long MIN_ANNUAL = 10_000_000L;
  private static final long MAX_ANNUAL = 1_000_000_000L;
  private static final int MONTHS = 12;
  private static final Pattern EOK = Pattern.compile("(\\d+)\\s*억(?:\\s*(\\d[\\d,]*)\\s*만)?");
  private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*");
  private static final Pattern NON_ANNUAL = Pattern.compile("시급|일급|주급");

  private SalaryTextParser() {}

  public static Optional<SalaryRange> parseAnnual(String text) {
    if (text == null || text.isBlank() || NON_ANNUAL.matcher(text).find()) {
      return Optional.empty();
    }
    boolean monthly = text.contains("월급");
    String normalized = expandEok(text);
    boolean manwon = normalized.contains("만");
    List<Long> values = new ArrayList<>();
    Matcher m = NUMBER.matcher(normalized);
    while (m.find() && values.size() < 2) {
      long raw = Long.parseLong(m.group().replace(",", ""));
      long won = manwon ? raw * MANWON : raw;
      values.add(monthly ? won * MONTHS : won);
    }
    if (values.isEmpty()) {
      return Optional.empty();
    }
    long min = values.get(0);
    long max = values.size() > 1 ? values.get(1) : min;
    if (min > max || min < MIN_ANNUAL || max > MAX_ANNUAL) {
      return Optional.empty();
    }
    return Optional.of(new SalaryRange(min, max));
  }

  /** 「1억 2,000만」 → 「12000만」, 「1억」 → 「10000만」. */
  private static String expandEok(String text) {
    Matcher m = EOK.matcher(text);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      long eok = Long.parseLong(m.group(1));
      long man = m.group(2) == null ? 0 : Long.parseLong(m.group(2).replace(",", ""));
      m.appendReplacement(sb, (eok * 10_000 + man) + "만");
    }
    m.appendTail(sb);
    return sb.toString();
  }
}
