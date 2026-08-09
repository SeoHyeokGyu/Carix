package com.carix.jobposting.seed;

import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SaraminListingEnricher {

  public SaraminListingFields extract(String listingPageHtml, String externalId) {
    Document doc = Jsoup.parse(listingPageHtml);
    Element item = doc.selectFirst("div.item_recruit[value=" + externalId + "]");
    if (item == null) {
      return new SaraminListingFields(null, null, List.of(), List.of());
    }

    Elements conditionSpans = item.select(".job_condition > span");
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

    return new SaraminListingFields(employmentType, postedEndDateRaw, skillTags, industryTags);
  }
}
