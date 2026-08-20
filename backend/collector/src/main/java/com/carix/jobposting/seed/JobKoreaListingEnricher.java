package com.carix.jobposting.seed;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class JobKoreaListingEnricher {

  private static final Pattern SALARY_PATTERN =
      Pattern.compile("([\\d,]+)\\s*(?:~\\s*([\\d,]+))?\\s*만원");

  public JobKoreaListingFields extract(String listingPageHtml, String externalId) {
    Document doc = Jsoup.parse(listingPageHtml);
    Element row = doc.selectFirst("tr.devloopArea[data-gno=" + externalId + "]");
    if (row == null) {
      return new JobKoreaListingFields(null, null, null, null, null, null);
    }

    Elements cells = row.select("p.etc span.cell");
    String employmentType = cells.size() > 3 ? textOrNull(cells.get(3)) : null;

    Long salaryMin = null;
    Long salaryMax = null;
    String positionLevel = null;

    if (cells.size() >= 6) {
      String salaryText = textOrNull(cells.get(4));
      if (salaryText != null) {
        Matcher matcher = SALARY_PATTERN.matcher(salaryText);
        if (matcher.find()) {
          salaryMin = parseAmount(matcher.group(1));
          salaryMax = matcher.group(2) != null ? parseAmount(matcher.group(2)) : salaryMin;
        }
      }
      positionLevel = textOrNull(cells.get(5));
    } else if (cells.size() == 5) {
      positionLevel = textOrNull(cells.get(4));
    }

    String companyBadge = null;
    Element typDiv = row.selectFirst("td.tplCo div.typ");
    if (typDiv != null) {
      companyBadge = textOrNull(typDiv);
    }

    String postedEndDateRaw = null;
    Element dateSpan = row.selectFirst("td.odd span.date");
    if (dateSpan != null) {
      postedEndDateRaw = dateSpan.text().strip();
    }

    return new JobKoreaListingFields(
        employmentType, salaryMin, salaryMax, positionLevel, companyBadge, postedEndDateRaw);
  }

  private String textOrNull(Element element) {
    String text = element.text().strip();
    return text.isEmpty() ? null : text;
  }

  private Long parseAmount(String rawNumber) {
    return Long.parseLong(rawNumber.replace(",", ""));
  }
}
