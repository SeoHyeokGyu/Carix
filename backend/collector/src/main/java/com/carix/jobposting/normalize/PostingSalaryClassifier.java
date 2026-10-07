package com.carix.jobposting.normalize;

import com.carix.jobposting.crawler.JobKoreaDetailParser;
import java.util.Optional;
import java.util.regex.Pattern;

/** 상세페이지에서 공고 급여를 판정한다. 설계 §5.1 — 금액이 보이면 공개, 회사내규·협의만이면 비공개 구간, 그 밖은 없음. */
public class PostingSalaryClassifier {

  private static final Pattern PAY_KEYWORD = Pattern.compile("연봉|월급");
  private static final Pattern DIGIT = Pattern.compile("\\d");

  private final JobKoreaDetailParser detailParser = new JobKoreaDetailParser();
  private final JobKoreaPayParser payParser = new JobKoreaPayParser();

  public PostingSalary classifyJobKorea(String detailHtml) {
    if (detailHtml == null) {
      return PostingSalary.unknown();
    }
    SalaryRange band = payParser.parseBand(detailParser.extractSalaryRaw(detailHtml));
    return classify(SalaryText.jobKorea(detailHtml), band);
  }

  public PostingSalary classifySaramin(String detailHtml) {
    if (detailHtml == null) {
      return PostingSalary.unknown();
    }
    return classify(SalaryText.saramin(detailHtml), null);
  }

  /** 상세페이지로 공개·비공개를 못 가렸을 때만 목록페이지 셀 금액(만원 또는 원)을 공개 연봉으로 쓴다. 셀에는 공개된 금액만 보인다. */
  public static PostingSalary withListingFallback(
      PostingSalary fromDetail, Long listingMin, Long listingMax) {
    boolean undecided =
        fromDetail.disclosure() == SalaryDisclosure.NONE
            || fromDetail.disclosure() == SalaryDisclosure.UNKNOWN;
    if (!undecided || listingMin == null || listingMax == null) {
      return fromDetail;
    }
    long min = toWon(listingMin);
    long max = toWon(listingMax);
    if (!SalaryTextParser.isPlausibleAnnual(min, max)) {
      return fromDetail;
    }
    return PostingSalary.publicRange(min, max);
  }

  static PostingSalary classify(String text, SalaryRange band) {
    if (text == null) {
      return PostingSalary.unknown();
    }
    if (text.isBlank()) {
      return PostingSalary.none();
    }
    Optional<SalaryRange> disclosed = SalaryTextParser.parseAnnual(text);
    if (disclosed.isPresent()) {
      return PostingSalary.publicRange(disclosed.get().min(), disclosed.get().max());
    }
    // 금액처럼 보이는데 못 읽었다 — 없다고 단정하지 않고 판정 보류로 둬 기존 값을 지킨다.
    if (PAY_KEYWORD.matcher(text).find() && DIGIT.matcher(text).find()) {
      return PostingSalary.unknown();
    }
    // 공개 금액이 없는데 회사 구간이 있으면 비공개 구간이다.
    return band != null ? PostingSalary.band(band) : PostingSalary.none();
  }

  private static long toWon(long value) {
    return value > 0 && value < 1_000_000 ? value * 10_000L : value;
  }
}
