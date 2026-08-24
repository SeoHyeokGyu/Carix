package com.carix.jobposting.normalize;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공고가 요구하는 경력 연차 범위. 원천 표기를 하한·상한으로 가른다.
 *
 * <p>사람인은 `경력 3~10년` 처럼 범위를 준다(실측 397건 중 135건). 하한만 담는 단일 정수로 뭉개면 `(\d+)년` 이 년 바로 앞 숫자인 상한을 잡아 3년차도
 * 지원 가능한 공고가 시니어로 분류된다. 잡코리아는 범위 표기를 쓰지 않는다(실측 400건 중 0건).
 *
 * <p>상한이 {@code null} 이면 상한 없음이고, 하한이 {@code null} 이면 연차를 알 수 없다는 뜻이다. 동료집단 브래킷은 하한으로 매긴다 — 공고가 받아
 * 주는 가장 낮은 연차가 비교 기준이기 때문이다({@link ExperienceBracket}).
 */
public record ExperienceRange(Integer min, Integer max) {

  private static final ExperienceRange UNKNOWN = new ExperienceRange(null, null);
  private static final ExperienceRange NO_FLOOR = new ExperienceRange(0, null);
  private static final ExperienceRange NEW_GRADUATE_ONLY = new ExperienceRange(0, 0);

  private static final Pattern RANGE = Pattern.compile("(\\d+)\\s*~\\s*(\\d+)\\s*년");
  private static final Pattern MINIMUM = Pattern.compile("(\\d+)\\s*년");

  public static ExperienceRange parse(String careerText) {
    if (careerText == null || careerText.isBlank()) {
      return UNKNOWN;
    }
    String text = careerText.strip();

    // 신입을 받으면 뒤에 붙은 경력 연차는 상한이 아니라 우대 조건이다 (`신입·경력3년↑`).
    if (text.contains("신입")) {
      return text.contains("경력") ? NO_FLOOR : NEW_GRADUATE_ONLY;
    }
    if (text.contains("무관")) {
      return NO_FLOOR;
    }

    Matcher range = RANGE.matcher(text);
    if (range.find()) {
      return new ExperienceRange(
          Integer.parseInt(range.group(1)), Integer.parseInt(range.group(2)));
    }

    Matcher bound = MINIMUM.matcher(text);
    if (bound.find()) {
      int years = Integer.parseInt(bound.group(1));
      // 화살표 방향이 의미를 뒤집는다. `경력6년↓` 는 6년 이하라 6 이 상한이다.
      return text.contains("↓")
          ? new ExperienceRange(null, years)
          : new ExperienceRange(years, null);
    }

    // `경력` 단독은 경력자를 원한다는 것만 알려 준다. 0 으로 두면 신입을 받는다는 거짓 진술이 된다.
    return UNKNOWN;
  }
}
