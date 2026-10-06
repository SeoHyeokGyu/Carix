package com.carix.jobposting.normalize;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 상세페이지 {@code <meta name="description">} 에서 급여 문구만 꺼낸다. */
public final class SalaryText {

  private static final Pattern DESCRIPTION =
      Pattern.compile("<meta\\s+name=\"description\"\\s+content=\"([^\"]*)\"");
  // 잡코리아: 「…, 급여 : 연봉 4,512만원, 마감일 : …」 — 필드 구분은 「, 」 뒤 「이름 : 」라 숫자 안 쉼표와 다르다.
  private static final Pattern JOBKOREA_PAY = Pattern.compile("급여 : (.*?)(?:, [^,:]+ : |$)");
  // 사람인: 공개면 「연봉:4,100 만원」·「월급:…」, 비공개면 이름 없이 「면접 후 결정」·「회사내규에 따름」.
  private static final Pattern SARAMIN_LABELED =
      Pattern.compile("(?:^|, )((?:연봉|월급|시급|일급|주급)\\s*:[^,]*(?:,\\d[^,]*)*)");
  private static final Pattern SARAMIN_HIDDEN =
      Pattern.compile("(?:^|, )((?:면접 후 결정|회사\\s?내규[^,]*))(?:,|$)");

  private SalaryText() {}

  public static String jobKorea(String html) {
    String d = description(html);
    if (d == null) {
      return null;
    }
    Matcher m = JOBKOREA_PAY.matcher(d);
    return m.find() ? m.group(1).strip() : null;
  }

  public static String saramin(String html) {
    String d = description(html);
    if (d == null) {
      return null;
    }
    Matcher labeled = SARAMIN_LABELED.matcher(d);
    if (labeled.find()) {
      return labeled.group(1).strip();
    }
    Matcher hidden = SARAMIN_HIDDEN.matcher(d);
    return hidden.find() ? hidden.group(1).strip() : null;
  }

  private static String description(String html) {
    if (html == null) {
      return null;
    }
    Matcher m = DESCRIPTION.matcher(html);
    return m.find() ? m.group(1) : null;
  }
}
