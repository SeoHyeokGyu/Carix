package com.carix.crawler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class JobKoreaCrawler {

  public List<JobPosting> crawl(String keyword, int pageLimit) {
    List<JobPosting> postings = new ArrayList<>();
    Set<String> collectedIds = new HashSet<>();

    // 잡코리아 IT/개발 분야 직무 분류 고유 코드: jobtype=9
    System.out.println(
        "[JobKoreaCrawler] Starting crawling JobKorea category (IT/개발 - jobtype=9) up to page "
            + pageLimit);

    for (int page = 1; page <= pageLimit; page++) {
      System.out.println("[JobKoreaCrawler] Crawling page " + page + "...");
      try {
        // 잡코리아 직종별 채용 리스트 주소 (직무분류 코드: jobtype=9, 페이징 파라미터: Page_No)
        String url = String.format(
            "https://www.jobkorea.co.kr/recruit/joblist?menucode=local&jobtype=9&Page_No=%d", page);

        Document doc = Jsoup.connect(url)
            .userAgent(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
            .timeout(10000)
            .get();

        // 직무 목록 행 선택자
        Elements rows = doc.select("tr.devloopArea");
        if (rows.isEmpty()) {
          System.out.println(
              "[JobKoreaCrawler] No more job listings found on page " + page + ". Stopping.");
          break;
        }

        for (Element row : rows) {
          // 공고 ID 추출 (data-gno 속성)
          String id = row.attr("data-gno").strip();
          if (id.isEmpty()) {
            continue;
          }

          if (collectedIds.contains(id)) {
            continue;
          }
          collectedIds.add(id);

          // 회사명
          String companyName = row.select(".tplCo a.link").text().strip();

          // 공고명
          Element titleLink = row.selectFirst(".tplTit strong a.link");
          if (titleLink == null) {
            continue;
          }
          String title = titleLink.text().strip();

          // 상세 링크
          String link = titleLink.attr("href").strip();
          if (link.startsWith("/")) {
            link = "https://www.jobkorea.co.kr" + link;
          }

          // 조건 정보들 (경력, 학력, 지역 등)
          Elements cellSpans = row.select(".tplTit p.etc span.cell");
          String careerType = "무관";
          String education = "학력무관";
          String location = "전국";

          if (cellSpans.size() > 0) {
            careerType = cellSpans.get(0).text().strip();
          }
          if (cellSpans.size() > 1) {
            education = cellSpans.get(1).text().strip();
          }
          if (cellSpans.size() > 2) {
            location = cellSpans.get(2).text().strip();
          }

          // 최소 경력 연차 추출
          int careerRange = extractCareerYears(careerType);

          // 등록일 추출
          String createdAt = "";
          Element timeSpan = row.selectFirst("td.odd span.time");
          if (timeSpan != null) {
            createdAt = timeSpan.text().strip();
          }

          postings.add(new JobPosting(
              "JOBKOREA",
              id,
              companyName,
              title,
              careerType,
              careerRange,
              education,
              location,
              link,
              createdAt
          ));
        }

        // 서버 트래픽 방지 딜레이
        Thread.sleep(1000);

      } catch (Exception e) {
        System.err.println("[JobKoreaCrawler] Error on page " + page + ": " + e.getMessage());
      }
    }

    System.out.println("[JobKoreaCrawler] Crawling finished. Total collected: " + postings.size());
    return postings;
  }

  private int extractCareerYears(String careerText) {
    if (careerText == null || careerText.isEmpty()) {
      return 0;
    }
    if (careerText.contains("무관") || careerText.contains("신입")) {
      return 0;
    }
    Pattern pattern = Pattern.compile("(\\d+)년");
    Matcher matcher = pattern.matcher(careerText);
    if (matcher.find()) {
      try {
        return Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }
}
