package com.carix.jobposting.crawler;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class SaraminCrawler {

  private Map<String, String> sessionCookies = new HashMap<>();

  public SaraminCrawler() {
    initSession();
  }

  private void initSession() {
    try {
      System.out.println(
          "[SaraminCrawler] Accessing Saramin main page to fetch session cookies...");
      org.jsoup.Connection.Response res =
          Jsoup.connect("https://www.saramin.co.kr/")
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .header(
                  "Accept",
                  "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
              .header("Accept-Language", "ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3")
              .timeout(10000)
              .execute();
      this.sessionCookies = res.cookies();
      System.out.println(
          "[SaraminCrawler] Successfully fetched " + sessionCookies.size() + " session cookies.");
    } catch (IOException e) {
      System.err.println(
          "[SaraminCrawler] Failed to initialize session cookies: " + e.getMessage());
    }
  }

  public List<CrawledJobItem> crawl(int pageLimit, String outputDir) {
    List<CrawledJobItem> postings = new ArrayList<>();
    Set<String> collectedIds = new HashSet<>();
    RawIngestWriter rawWriter = new RawIngestWriter(Path.of(outputDir));

    // IT개발/데이터 직군 상세 코드들 (84=백엔드, 86=프론트엔드, 87=웹개발, 92=앱개발)
    System.out.println(
        "[SaraminCrawler] Starting crawling Saramin job categories (IT개발/데이터) up to page "
            + pageLimit);

    for (int page = 1; page <= pageLimit; page++) {
      System.out.println("[SaraminCrawler] Crawling page " + page + "...");
      try {
        // 직무코드(job_cd=84,86,87,92) 조건과 검색 조건을 적용한 사람인 검색 리스트 주소
        String url =
            String.format(
                "https://www.saramin.co.kr/zf_user/search/recruit?searchword=%%EA%%B0%%9C%%EB%%B0%%9C%%EC%%9E%%90&job_cd=84&job_cd=86&job_cd=87&job_cd=92&recruitPage=%d&recruitSort=relation&recruitPageCount=40",
                page);

        Document doc = buildConnection(url, sessionCookies).get();

        rawWriter.append(
            new RawIngestItem("SARAMIN", "listpage-" + page, doc.outerHtml(), Instant.now()));

        Elements items = doc.select("div.item_recruit");
        if (items.isEmpty()) {
          System.out.println(
              "[SaraminCrawler] No more items found on page " + page + ". Stopping.");
          break;
        }

        for (Element item : items) {
          String id = item.attr("value");
          if (id == null || id.trim().isEmpty()) {
            id = item.attr("id");
          }
          if (id == null || id.trim().isEmpty()) {
            continue;
          }

          if (collectedIds.contains(id)) {
            continue;
          }
          collectedIds.add(id);

          // 회사명
          String companyName = item.select(".area_corp strong.corp_name a").text().strip();
          if (companyName.isEmpty()) {
            companyName = item.select(".area_corp a.corp_name").text().strip();
          }

          // 공고 제목
          Element titleLink = item.selectFirst(".area_job .job_tit a");
          if (titleLink == null) {
            continue;
          }
          String title = titleLink.text().strip();

          // 상세 링크
          String link = titleLink.attr("href").strip();
          if (link.startsWith("/")) {
            link = "https://www.saramin.co.kr" + link;
          }

          // 조건 정보 (지역, 경력, 학력 등)
          Elements conditions = item.select(".area_job .job_condition span");
          String location = "";
          String careerType = "";
          String education = "";

          if (conditions.size() > 0) {
            location = conditions.get(0).text().strip();
          }
          if (conditions.size() > 1) {
            careerType = conditions.get(1).text().strip();
          }
          if (conditions.size() > 2) {
            education = conditions.get(2).text().strip();
          }

          // 최소 경력 연차 추출
          int careerRange = extractCareerYears(careerType);

          // 등록일 추출
          String createdAt = "";
          Element daySpan = item.selectFirst(".area_job .job_sector .job_day");
          if (daySpan != null) {
            String dayText = daySpan.text().strip();
            createdAt = dayText.replace("등록일 ", "").trim();
          }

          postings.add(
              new CrawledJobItem(
                  "SARAMIN",
                  id,
                  companyName,
                  title,
                  careerType,
                  careerRange,
                  education,
                  location,
                  link,
                  createdAt,
                  List.of(),
                  ""));
        }

        // 서버 트래픽 방지 딜레이
        Thread.sleep(1000);

      } catch (Exception e) {
        System.err.println("[SaraminCrawler] Error on page " + page + ": " + e.getMessage());
      }
    }

    System.out.println("[SaraminCrawler] Crawling finished. Total collected: " + postings.size());
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

  static Connection buildConnection(String url, Map<String, String> cookies) {
    return Jsoup.connect(url)
        .userAgent(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .header(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
        .header("Referer", "https://www.saramin.co.kr/")
        .cookies(cookies)
        .maxBodySize(0)
        .timeout(10000);
  }
}
