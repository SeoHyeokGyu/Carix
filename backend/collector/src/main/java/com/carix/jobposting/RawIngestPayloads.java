package com.carix.jobposting;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * `raw_ingest.payload` 에 담을 원문을 다듬는다.
 *
 * <p>목록페이지는 `<script>`·`<style>` 이 본문의 대부분을 차지한다(사람인 실측 2234kB 중 1836kB). 목록페이지를 읽는 코드는
 * `SaraminListingEnricher`·`JobKoreaListingEnricher`·`ListingPageIndex` 셋 뿐이고 전부 jsoup DOM 셀렉터만
 * 쓰므로, 둘을 떼어내도 재파싱 결과가 달라지지 않는다.
 *
 * <p>상세페이지는 대상이 아니다 — 잡코리아 상세는 급여·스킬·사원수가 `<script>` 안의 Next.js RSC 페이로드에 들어 있어 ({@link
 * com.carix.crawler.JobKoreaDetailParser}), script 를 떼면 그 데이터가 통째로 사라진다.
 */
public final class RawIngestPayloads {

  private RawIngestPayloads() {}

  /** 목록페이지 원문에서 파싱에 쓰이지 않는 script·style 을 떼어낸다. */
  public static String listpage(String html) {
    Document doc = Jsoup.parse(html);
    doc.select("script, style").remove();
    return doc.outerHtml();
  }
}
