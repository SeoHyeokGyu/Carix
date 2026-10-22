package com.carix.jobposting.normalize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SalaryTextParserTest {

  private static SalaryRange p(String text) {
    return SalaryTextParser.parseAnnual(text).orElse(null);
  }

  @Test
  void annualSingle() {
    assertThat(p("연봉 4,512만원")).isEqualTo(new SalaryRange(45_120_000L, 45_120_000L));
  }

  @Test
  void annualRangeWithUnitOnlyAtEnd() {
    assertThat(p("연봉 4,000~6,000만원")).isEqualTo(new SalaryRange(40_000_000L, 60_000_000L));
  }

  @Test
  void annualAtLeastBecomesPoint() {
    assertThat(p("연봉 5,000만원 이상(면접 후 결정)")).isEqualTo(new SalaryRange(50_000_000L, 50_000_000L));
  }

  @Test
  void monthlyWithInterviewNoteIsPublic() {
    assertThat(p("월급 500~600만원(면접 후 결정)")).isEqualTo(new SalaryRange(60_000_000L, 72_000_000L));
  }

  @Test
  void saraminColonStyle() {
    assertThat(p("연봉:4,100 만원")).isEqualTo(new SalaryRange(41_000_000L, 41_000_000L));
  }

  @Test
  void parsesEok() {
    assertThat(p("연봉 1억")).isEqualTo(new SalaryRange(100_000_000L, 100_000_000L));
    assertThat(p("연봉 1억 2,000만원")).isEqualTo(new SalaryRange(120_000_000L, 120_000_000L));
  }

  @Test
  void notPublic() {
    assertThat(p("회사 내규에 따름(면접 후 결정)")).isNull();
    assertThat(p("면접 후 결정")).isNull();
    assertThat(p("시급 10,030원")).isNull();
    assertThat(p("연봉 4")).isNull(); // 1,000만원 미만은 잡음
    assertThat(p(null)).isNull();
  }

  @Test
  void ignoresTrailingParentheticalsAndNotes() {
    assertThat(p("연봉 3,600만원 (월 300만원)")).isEqualTo(new SalaryRange(36_000_000L, 36_000_000L));
    assertThat(p("연봉 4,000만원 (주 5일)")).isEqualTo(new SalaryRange(40_000_000L, 40_000_000L));
    assertThat(p("연봉 4,000만원, 성과급 300%")).isEqualTo(new SalaryRange(40_000_000L, 40_000_000L));
    assertThat(p("월급 300만원 (연 3,600만원)")).isEqualTo(new SalaryRange(36_000_000L, 36_000_000L));
    assertThat(p("연봉 45,000,000원 (만 3년 이상)")).isEqualTo(new SalaryRange(45_000_000L, 45_000_000L));
  }

  @Test
  void rangesAndOneSidedBounds() {
    assertThat(p("연봉 1억~1억 5,000만원")).isEqualTo(new SalaryRange(100_000_000L, 150_000_000L));
    assertThat(p("월급 300만원 이상")).isEqualTo(new SalaryRange(36_000_000L, 36_000_000L));
    assertThat(p("연봉 3,600만원 이하")).isEqualTo(new SalaryRange(36_000_000L, 36_000_000L));
    assertThat(p("연봉 3,000만원~4,000만원")).isEqualTo(new SalaryRange(30_000_000L, 40_000_000L));
  }

  @Test
  void notPublicWhenOnlyNumbersInNotes() {
    assertThat(p("회사내규 (2024년 만근 시)")).isNull();
  }

  @Test
  void parsesCheonUnits() {
    assertThat(p("연봉 4천만원")).isEqualTo(new SalaryRange(40_000_000L, 40_000_000L));
    assertThat(p("연봉 1억 2천만원")).isEqualTo(new SalaryRange(120_000_000L, 120_000_000L));
    assertThat(p("연봉 3천~4천만원")).isEqualTo(new SalaryRange(30_000_000L, 40_000_000L));
    assertThat(p("연봉 1억 2천 500만원")).isEqualTo(new SalaryRange(125_000_000L, 125_000_000L));
  }

  @Test
  void rejectsPartialEokMatch() {
    assertThat(p("연봉 1억 2백만원")).isNull();
    assertThat(p("연봉 1억 5000")).isNull();
  }

  @Test
  void overlongDigitsAreUnparseableWithoutException() {
    assertThat(p("연봉 12345678901234567890만원")).isNull();
    assertThat(p("연봉 99999999999999999999억")).isNull();
  }

  @Test
  void rejectsImplausibleAmounts() {
    // 원문 오기: 연봉을 월급으로 적었거나 0 을 하나 더 붙였다
    assertThat(p("월급 4,000~8,000만원(면접 후 결정)")).isNull();
    assertThat(p("월급 2,700~2,700만원")).isNull();
    assertThat(p("연봉 30,000~50,000만원(면접 후 결정)")).isNull();
    assertThat(p("연봉 3억 1,000만원")).isNull();
    assertThat(p("월급 1,600만원")).isNull();
  }

  @Test
  void keepsHighButPlausibleAmounts() {
    assertThat(p("연봉 3억")).isEqualTo(new SalaryRange(300_000_000L, 300_000_000L));
    assertThat(p("월급 1,500만원")).isEqualTo(new SalaryRange(180_000_000L, 180_000_000L));
  }
}
