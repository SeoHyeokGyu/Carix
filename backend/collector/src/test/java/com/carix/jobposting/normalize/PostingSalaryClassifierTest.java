package com.carix.jobposting.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostingSalaryClassifierTest {

  private final PostingSalaryClassifier classifier = new PostingSalaryClassifier();

  private static String jobKoreaHtml(String salaryText, String payJson) {
    String escaped = payJson == null ? "" : payJson.replace("\"", "\\\"");
    return "<html><head><meta name=\"description\" content=\"경력 : 경력 5년이상, 학력 : 학력무관, 급여 : "
        + salaryText
        + ", 마감일 : 2026.09.30\"/></head><body><script>self.__next_f.push([1,\"{\\\"pay\\\":"
        + escaped
        + ",\\\"benefitCodes\\\":[]}\"])</script></body></html>";
  }

  @Test
  void jobKoreaCompanyRuleWithBandIsBand() {
    String html =
        jobKoreaHtml(
            "회사 내규에 따름(면접 후 결정)",
            "{\"payType\":\"ANNUALLY_SALARY\",\"payRange\":{\"from\":2590,\"to\":10001,\"fromInclusive\":true,\"toInclusive\":false}}");

    PostingSalary s = classifier.classifyJobKorea(html);

    assertThat(s.disclosure()).isEqualTo(SalaryDisclosure.BAND);
    assertThat(s.min()).isNull();
    assertThat(s.bandMin()).isEqualTo(25_900_000L);
    assertThat(s.bandMax()).isEqualTo(100_000_000L);
  }

  @Test
  void jobKoreaTextKeepsThousandsSeparator() {
    PostingSalary s = classifier.classifyJobKorea(jobKoreaHtml("연봉 4,512만원", null));

    assertThat(s.disclosure()).isEqualTo(SalaryDisclosure.PUBLIC);
    assertThat(s.min()).isEqualTo(45_120_000L);
    assertThat(s.max()).isEqualTo(45_120_000L);
  }

  @Test
  void jobKoreaPublicMonthly() {
    String html =
        jobKoreaHtml(
            "월급 500~600만원(면접 후 결정)",
            "{\"payType\":\"MONTHLY_SALARY\",\"payRange\":{\"from\":500,\"to\":601,\"fromInclusive\":true,\"toInclusive\":false}}");

    PostingSalary s = classifier.classifyJobKorea(html);

    assertThat(s.disclosure()).isEqualTo(SalaryDisclosure.PUBLIC);
    assertThat(s.min()).isEqualTo(60_000_000L);
    assertThat(s.max()).isEqualTo(72_000_000L);
    assertThat(s.bandMin()).isNull();
  }

  @Test
  void jobKoreaCompanyRuleWithoutBandIsNone() {
    assertThat(classifier.classifyJobKorea(jobKoreaHtml("회사내규에 따름", null)).disclosure())
        .isEqualTo(SalaryDisclosure.NONE);
  }

  @Test
  void nullHtmlIsUnknown() {
    assertThat(classifier.classifyJobKorea(null).disclosure()).isEqualTo(SalaryDisclosure.UNKNOWN);
    assertThat(classifier.classifySaramin(null).disclosure()).isEqualTo(SalaryDisclosure.UNKNOWN);
  }

  @Test
  void saraminPublicAndHidden() {
    String pub =
        "<meta name=\"description\" content=\"옵티시스(주), 영상전송용 하드웨어 개발자 채용, 경력:경력무관, 학력:대학교졸업(4년)이상, 연봉:4,100 만원, 마감일:2026-11-14, 홈페이지:www.opticis.com\"/>";
    String hidden =
        "<meta name=\"description\" content=\"(주)타임리, 백엔드 개발자 채용, 경력:경력 4년 이상, 학력:학력무관, 면접 후 결정, 마감일:2026-10-18, 홈페이지:www.timely.kr\"/>";

    PostingSalary a = classifier.classifySaramin(pub);
    PostingSalary b = classifier.classifySaramin(hidden);

    assertThat(a.disclosure()).isEqualTo(SalaryDisclosure.PUBLIC);
    assertThat(a.min()).isEqualTo(41_000_000L);
    assertThat(b.disclosure()).isEqualTo(SalaryDisclosure.NONE);
  }

  @Test
  void listingFallbackOnlyWhenDetailHasNoVerdict() {
    PostingSalary none = PostingSalary.none();
    PostingSalary band = PostingSalary.band(new SalaryRange(30_000_000L, 40_000_000L));

    assertThat(PostingSalaryClassifier.withListingFallback(none, 4000L, 6000L))
        .isEqualTo(PostingSalary.publicRange(40_000_000L, 60_000_000L));
    assertThat(PostingSalaryClassifier.withListingFallback(PostingSalary.unknown(), null, null))
        .isEqualTo(PostingSalary.unknown());
    assertThat(PostingSalaryClassifier.withListingFallback(band, 4000L, 6000L)).isEqualTo(band);
  }

  @Test
  void jobKoreaSalaryAsLastField() {
    String html = "<meta name=\"description\" content=\"경력 : 경력무관, 급여 : 연봉 4,000~6,000만원\"/>";

    PostingSalary s = classifier.classifyJobKorea(html);

    assertThat(s.disclosure()).isEqualTo(SalaryDisclosure.PUBLIC);
    assertThat(s.min()).isEqualTo(40_000_000L);
    assertThat(s.max()).isEqualTo(60_000_000L);
  }

  @Test
  void missingDescriptionOrSalaryFieldIsUnknown() {
    assertThat(classifier.classifyJobKorea("<html><body>blocked</body></html>").disclosure())
        .isEqualTo(SalaryDisclosure.UNKNOWN);
    assertThat(
            classifier
                .classifyJobKorea("<meta name=\"description\" content=\"경력 : 경력무관\"/>")
                .disclosure())
        .isEqualTo(SalaryDisclosure.UNKNOWN);
    assertThat(classifier.classifySaramin("<html></html>").disclosure())
        .isEqualTo(SalaryDisclosure.UNKNOWN);
  }

  @Test
  void undisclosedTextWithBandIsBand() {
    PostingSalary s =
        classifier.classifyJobKorea(
            jobKoreaHtml(
                "추후 결정",
                "{\"payType\":\"ANNUALLY_SALARY\",\"payRange\":{\"from\":3000,\"to\":5001,\"fromInclusive\":true,\"toInclusive\":false}}"));

    assertThat(s.disclosure()).isEqualTo(SalaryDisclosure.BAND);
    assertThat(s.bandMin()).isEqualTo(30_000_000L);
  }

  @Test
  void payKeywordWithDigitsButUnparsedIsUnknownEvenWithBand() {
    PostingSalary s =
        classifier.classifyJobKorea(
            jobKoreaHtml(
                "연봉 3000~4000",
                "{\"payType\":\"ANNUALLY_SALARY\",\"payRange\":{\"from\":3000,\"to\":5001,\"fromInclusive\":true,\"toInclusive\":false}}"));

    assertThat(s.disclosure()).isEqualTo(SalaryDisclosure.UNKNOWN);
  }

  @Test
  void listingFallbackIgnoresOutOfRangeValues() {
    PostingSalary none = PostingSalary.none();

    assertThat(PostingSalaryClassifier.withListingFallback(none, 5L, 6L)).isEqualTo(none);
    assertThat(PostingSalaryClassifier.withListingFallback(none, 4000L, 900_000L)).isEqualTo(none);
    assertThat(PostingSalaryClassifier.withListingFallback(none, 6000L, 4000L)).isEqualTo(none);
  }

  @Test
  void implausibleMonthlyAmountIsUnknown() {
    assertThat(
            PostingSalaryClassifier.classify(
                    "월급 4,000~8,000만원(면접 후 결정)", new SalaryRange(48_000_000L, 96_000_000L))
                .disclosure())
        .isEqualTo(SalaryDisclosure.UNKNOWN);
  }
}
