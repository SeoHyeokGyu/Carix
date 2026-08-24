package com.carix.jobposting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.carix.jobposting.seed.SaraminListingEnricher;
import com.carix.jobposting.seed.SaraminListingFields;
import org.junit.jupiter.api.Test;

class RawIngestPayloadsTest {

  private static final String LISTPAGE_HTML =
      """
      <html><head>
        <style>.job_sector{color:#333}</style>
        <script>window.__INITIAL__={heavy:"...수백 KB..."};</script>
      </head><body>
        <div class="item_recruit" value="54616506">
          <div class="area_job">
            <div class="job_sector">
              <a href="/zf_user/search/recruit?cat_kewd=84">Python</a>
              <a href="/zf_user/search/recruit?ind_key=201">SI</a>
            </div>
            <div class="job_condition"><span>서울</span><span>3년</span><span>대졸</span><span>정규직</span></div>
            <div class="job_date"><span class="date">~ 08/31(월)</span></div>
          </div>
        </div>
        <script src="/js/tracker.js"></script>
      </body></html>
      """;

  @Test
  void dropsScriptAndStyleFromAListpagePayload() {
    String compacted = RawIngestPayloads.listpage(LISTPAGE_HTML);

    assertFalse(compacted.contains("__INITIAL__"), "인라인 script 본문이 남았다");
    assertFalse(compacted.contains("tracker.js"), "외부 script 태그가 남았다");
    assertFalse(compacted.contains("color:#333"), "style 본문이 남았다");
  }

  /** 목록페이지 파서는 DOM 셀렉터만 쓴다. 줄인 payload 로 재파싱해도 결과가 같아야 저장을 줄일 수 있다. */
  @Test
  void keepsEveryFieldTheListingEnricherReads() {
    SaraminListingEnricher enricher = new SaraminListingEnricher();

    SaraminListingFields fromOriginal = enricher.extract(LISTPAGE_HTML, "54616506");
    SaraminListingFields fromCompacted =
        enricher.extract(RawIngestPayloads.listpage(LISTPAGE_HTML), "54616506");

    assertEquals(fromOriginal, fromCompacted);
    assertEquals(java.util.List.of("Python"), fromCompacted.skillTags());
    assertEquals(java.util.List.of("SI"), fromCompacted.industryTags());
    assertEquals("정규직", fromCompacted.employmentType());
    assertEquals("~ 08/31(월)", fromCompacted.postedEndDateRaw());
  }

  /** 공고를 목록에 이어 붙이는 식별자가 사라지면 `ListingPageIndex` 가 공고를 못 찾는다. */
  @Test
  void keepsTheItemIdentifiersTheListingIndexScans() {
    String compacted = RawIngestPayloads.listpage(LISTPAGE_HTML);

    assertTrue(
        org.jsoup.Jsoup.parse(compacted).select("div.item_recruit[value]").size() == 1,
        "목록 항목 식별자가 사라졌다");
  }

  @Test
  void leavesPayloadsWithNothingToDropUnharmed() {
    String plain = "<div class=\"item_recruit\" value=\"1\"></div>";

    assertTrue(
        org.jsoup.Jsoup.parse(RawIngestPayloads.listpage(plain))
                .select("div.item_recruit[value=1]")
                .size()
            == 1);
  }
}
