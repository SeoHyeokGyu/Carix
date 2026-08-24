package com.carix.jobposting.seed;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SaraminListingEnricher {

  public SaraminListingFields extract(String listingPageHtml, String externalId) {
    return extract(Jsoup.parse(listingPageHtml), externalId);
  }

  /** 목록페이지 하나에 공고 40건이 실려 있다. 같은 페이지를 공고마다 다시 파싱하지 않도록 이미 파싱된 문서를 받는 경로를 따로 둔다. */
  public SaraminListingFields extract(Document doc, String externalId) {
    Element item = doc.selectFirst("div.item_recruit[value=" + externalId + "]");
    if (item == null) {
      return new SaraminListingFields(null, null, null, List.of(), List.of());
    }

    // 조건 칸은 지역·경력·학력·고용형태 순이다. 칸이 모자란 공고가 있어 개수를 확인하고 읽는다.
    Elements conditionSpans = item.select(".job_condition > span");
    String careerRaw = conditionSpans.size() > 1 ? conditionSpans.get(1).text().strip() : null;
    String employmentType = conditionSpans.size() > 3 ? conditionSpans.get(3).text().strip() : null;

    String postedEndDateRaw = null;
    Element dateSpan = item.selectFirst(".job_date .date");
    if (dateSpan != null) {
      postedEndDateRaw = dateSpan.text().strip();
    }

    List<String> skillTags = new ArrayList<>();
    for (Element a : item.select(".job_sector a[href*=cat_kewd]")) {
      skillTags.add(a.text().strip());
    }

    List<String> industryTags = new ArrayList<>();
    for (Element a : item.select(".job_sector a[href*=ind_key]")) {
      industryTags.add(a.text().strip());
    }

    return new SaraminListingFields(
        careerRaw, employmentType, postedEndDateRaw, skillTags, industryTags);
  }
}
