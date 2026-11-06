package com.carix.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carix.index.enums.IndexStatus;
import com.carix.snapshot.DecisionSignalCode;
import com.carix.snapshot.domain.SnapshotRationale.OpeningWindowEvidence;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DecisionTimingEvidenceTest {

  private static final String UP_SENTENCE =
      "2026년 6~8월 내 동료집단 공고 비중이 이전 12개월보다 늘었습니다 (22.6% → 23.8%).";
  private static final String OWI = "105.60";

  private static OpeningWindowEvidence evidence(
      String baseline,
      String recent,
      String se,
      String from,
      String to,
      Boolean full,
      Integer lvl) {
    return new OpeningWindowEvidence(
        520,
        915,
        recent == null ? null : new BigDecimal(recent),
        baseline == null ? null : new BigDecimal(baseline),
        1,
        "전국 / 4~7년 / 회사규모 무관 / 고용형태 무관",
        2000,
        se == null ? null : new BigDecimal(se),
        from,
        to,
        full,
        lvl);
  }

  private static OpeningWindowEvidence base() {
    return evidence("22.56", "23.83", "5.60", "202606", "202608", true, 1);
  }

  private static String sentence(String owi, OpeningWindowEvidence evidence) {
    return DecisionTimingEvidence.sentence(
        DecisionSignalCode.NEGOTIATE,
        owi == null ? null : new BigDecimal(owi),
        IndexStatus.OK,
        evidence);
  }

  @Test
  @DisplayName("늘어난 경우 — 차이가 1σ 와 같아도 표시한다")
  void 늘어남_경계값() {
    assertThat(sentence(OWI, base())).isEqualTo(UP_SENTENCE);
  }

  @Test
  @DisplayName("이직 탐색도 대상이다")
  void 이직탐색() {
    assertThat(
            DecisionTimingEvidence.sentence(
                DecisionSignalCode.SEEK_JOB, new BigDecimal(OWI), IndexStatus.OK, base()))
        .isEqualTo(UP_SENTENCE);
  }

  @Test
  @DisplayName("줄어든 경우도 똑같이 보여 준다 — 늘었을 때만 보이면 판정을 미는 문구가 된다")
  void 줄어듦_경계값() {
    assertThat(sentence("94.40", evidence("23.83", "22.10", "5.60", "202606", "202608", true, 1)))
        .isEqualTo("2026년 6~8월 내 동료집단 공고 비중이 이전 12개월보다 줄었습니다 (23.8% → 22.1%).");
  }

  @Test
  @DisplayName("1σ 안이면 비슷한지도 알 수 없어 생략한다")
  void 잡음_안() {
    assertThat(sentence("105.59", base())).isNull();
  }

  @Test
  @DisplayName("연도가 갈리면 연도를 둘 다 적는다")
  void 연도_경계() {
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", "202512", "202602", true, 1)))
        .startsWith("2025년 12월~2026년 2월 내 동료집단 공고 비중이");
  }

  @Test
  @DisplayName("결합 원천이 완화 2단계면 내 동료집단이라 쓰지 않는다")
  void 완화_2단계() {
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", "202606", "202608", true, 2)))
        .startsWith("2026년 6~8월 인접 경력을 포함한 동료집단 공고 비중이");
  }

  @ParameterizedTest
  @EnumSource(
      value = DecisionSignalCode.class,
      names = {"SEEK_JOB", "NEGOTIATE"},
      mode = EnumSource.Mode.EXCLUDE)
  @DisplayName("그 외 판정에는 붙이지 않는다")
  void 대상_아닌_판정(DecisionSignalCode code) {
    assertThat(DecisionTimingEvidence.sentence(code, new BigDecimal(OWI), IndexStatus.OK, base()))
        .isNull();
  }

  @Test
  @DisplayName("상태가 OK 가 아니면 비중이 채워져 있어도 생략한다 — 과거 행은 상태가 null 이다")
  void 상태() {
    BigDecimal owi = new BigDecimal(OWI);
    DecisionSignalCode code = DecisionSignalCode.NEGOTIATE;
    assertThat(DecisionTimingEvidence.sentence(code, owi, IndexStatus.INSUFFICIENT_SAMPLE, base()))
        .isNull();
    assertThat(DecisionTimingEvidence.sentence(code, owi, null, base())).isNull();
  }

  @Test
  @DisplayName("재료가 하나라도 없으면 조용히 생략한다 — 이 필드 도입 전 행을 예외 없이 지난다")
  void 재료_없음() {
    assertThat(sentence(OWI, null)).isNull();
    assertThat(sentence(null, base())).isNull();
    assertThat(sentence(OWI, evidence(null, "23.83", "5.60", "202606", "202608", true, 1)))
        .isNull();
    assertThat(sentence(OWI, evidence("22.56", null, "5.60", "202606", "202608", true, 1)))
        .isNull();
    assertThat(sentence(OWI, evidence("22.56", "23.83", null, "202606", "202608", true, 1)))
        .isNull();
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", null, "202608", true, 1))).isNull();
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", "202606", null, true, 1))).isNull();
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", "202606", "202608", null, 1)))
        .isNull();
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", "202606", "202608", true, null)))
        .isNull();
  }

  @Test
  @DisplayName("결합 원천이 두 창을 다 채우지 않았으면 「이전 12개월」이라 말할 수 없다")
  void 창_미충족() {
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", "202606", "202608", false, 1)))
        .isNull();
  }

  @Test
  @DisplayName("지수와 비중의 방향이 다르면 같은 화면의 카드 뱃지와 모순되므로 생략한다")
  void 방향_불일치() {
    assertThat(sentence("107.00", evidence("23.00", "22.00", "5.60", "202606", "202608", true, 1)))
        .isNull();
  }

  @Test
  @DisplayName("지수가 정확히 100 이면 표준오차가 0 이어도 방향이 없어 생략한다")
  void 정확히_100() {
    assertThat(sentence("100.00", evidence("22.56", "23.83", "0.00", "202606", "202608", true, 1)))
        .isNull();
  }

  @Test
  @DisplayName("반올림 뒤 두 비중이 같으면 「늘었습니다 (22.6% → 22.6%)」가 되어 생략한다")
  void 반올림_뒤_같음() {
    assertThat(sentence(OWI, evidence("22.61", "22.64", "5.60", "202606", "202608", true, 1)))
        .isNull();
  }

  @Test
  @DisplayName("저장된 월 형식이 깨져도 대시보드를 막지 않고 생략한다")
  void 월_형식_깨짐() {
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", "2026-06", "202608", true, 1)))
        .isNull();
    assertThat(sentence(OWI, evidence("22.56", "23.83", "5.60", "202613", "202608", true, 1)))
        .isNull();
  }
}
