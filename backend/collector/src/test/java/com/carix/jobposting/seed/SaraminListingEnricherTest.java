package com.carix.jobposting.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SaraminListingEnricherTest {

  private final SaraminListingEnricher enricher = new SaraminListingEnricher();

  private static String listing(String... conditionSpans) {
    StringBuilder spans = new StringBuilder();
    for (String span : conditionSpans) {
      spans.append("<span>").append(span).append("</span>");
    }
    return """
        <div class="item_recruit" value="54616506">
          <div class="area_job">
            <div class="job_condition">%s</div>
            <div class="job_date"><span class="date">~ 08/31(월)</span></div>
          </div>
        </div>
        """
        .formatted(spans);
  }

  /** 백필이 `raw_ingest` 목록 원문에서 경력을 다시 읽어야 기존 공고의 잘못된 연차를 고칠 수 있다. 지역·경력·학력·고용형태 순으로 실려 있다. */
  @Test
  void readsTheRawCareerCell() {
    SaraminListingFields fields =
        enricher.extract(listing("서울 강남구", "경력 3~10년", "대졸↑", "정규직"), "54616506");

    assertEquals("경력 3~10년", fields.careerRaw());
  }

  @Test
  void stillReadsTheEmploymentTypeCell() {
    SaraminListingFields fields =
        enricher.extract(listing("서울 강남구", "경력 3~10년", "대졸↑", "정규직"), "54616506");

    assertEquals("정규직", fields.employmentType());
  }

  /** 조건 칸이 모자란 공고가 있다. 없으면 null 이지 잘못된 칸을 읽어서는 안 된다. */
  @Test
  void leavesTheCareerCellNullWhenAbsent() {
    SaraminListingFields fields = enricher.extract(listing("서울 강남구"), "54616506");

    assertNull(fields.careerRaw());
  }

  @Test
  void returnsEmptyFieldsForAnUnknownPosting() {
    SaraminListingFields fields =
        enricher.extract(listing("서울 강남구", "경력 3~10년", "대졸↑", "정규직"), "99999999");

    assertNull(fields.careerRaw());
    assertNull(fields.employmentType());
  }
}
