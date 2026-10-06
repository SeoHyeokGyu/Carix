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
  void wonUnit() {
    assertThat(p("연봉 45,000,000원")).isEqualTo(new SalaryRange(45_000_000L, 45_000_000L));
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
}
