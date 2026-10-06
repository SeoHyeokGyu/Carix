package com.carix.jobposting.normalize;

import com.carix.jobposting.crawler.JobKoreaDetailParser;
import java.util.Optional;
import java.util.regex.Pattern;

/** 상세페이지에서 공고 급여를 판정한다. 설계 §5.1 — 금액이 보이면 공개, 회사내규·협의만이면 비공개 구간, 그 밖은 없음. */
public class PostingSalaryClassifier {

  private static final Pattern NON_DISCLOSED = Pattern.compile("회사\\s?내규|면접\\s?후|협의");

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
    return PostingSalary.publicRange(toWon(listingMin), toWon(listingMax));
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
    if (NON_DISCLOSED.matcher(text).find() && band != null) {
      return PostingSalary.band(band);
    }
    return PostingSalary.none();
  }

  private static long toWon(long value) {
    return value > 0 && value < 1_000_000 ? value * 10_000L : value;
  }
}
