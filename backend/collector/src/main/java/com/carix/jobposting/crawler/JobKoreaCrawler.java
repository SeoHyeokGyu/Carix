package com.carix.jobposting.crawler;

import java.nio.file.Path;
import java.time.Instant;
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

  // 목록 페이징은 화면상의 joblist 주소가 아니라 이 XHR 엔드포인트가 담당한다.
  private static final String LIST_ENDPOINT = "https://www.jobkorea.co.kr/Recruit/Home/_GI_List/";

  // 직무 분류(dutyCtgr) "AI·개발·데이터" 대분류 코드. 하위 세부 직무(백엔드개발자 1000229 등)는
  // condition[duty]로 따로 보내야 하며, condition[duty]에 대분류 코드를 넣으면 무시된다.
  private static final String DUTY_CATEGORY_IT = "10031";

  private static final String LIST_REFERER =
      "https://www.jobkorea.co.kr/recruit/joblist?menucode=duty&dutyCtgr=" + DUTY_CATEGORY_IT;

  private final JobKoreaDetailParser detailParser = new JobKoreaDetailParser();

  public List<CrawledJobItem> crawl(String keyword, int pageLimit, String outputDir) {
    List<CrawledJobItem> postings = new ArrayList<>();
    Set<String> collectedIds = new HashSet<>();
    RawIngestWriter rawWriter = new RawIngestWriter(Path.of(outputDir));

    System.out.println(
        "[JobKoreaCrawler] Starting crawling JobKorea category (AI·개발·데이터 - dutyCtgr="
            + DUTY_CATEGORY_IT
            + ") up to page "
            + pageLimit);

    for (int page = 1; page <= pageLimit; page++) {
      System.out.println("[JobKoreaCrawler] Crawling page " + page + "...");
      try {
        // 목록 조회는 폼 인코딩 POST로만 페이지가 넘어간다. GET 주소의 Page_No 파라미터는 무시된다.
        Document doc =
            Jsoup.connect(LIST_ENDPOINT)
                .userAgent(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("X-Requested-With", "XMLHttpRequest")
                .referrer(LIST_REFERER)
                .data("page", String.valueOf(page))
                .data("condition[dutyCtgr]", DUTY_CATEGORY_IT)
                .data("condition[menucode]", "")
                .data("direct", "0")
                .data("order", "20")
                .data("pagesize", "40")
                .data("tabindex", "0")
                .data("onePick", "0")
                .data("confirm", "0")
                .data("profile", "0")
                .timeout(10000)
                .post();

        rawWriter.append(
            new RawIngestItem("JOBKOREA", "listpage-" + page, doc.outerHtml(), Instant.now()));

        // 직무 목록 행 선택자
        Elements rows = doc.select("tr.devloopArea");
        if (rows.isEmpty()) {
          System.out.println(
              "[JobKoreaCrawler] No more job listings found on page " + page + ". Stopping.");
          break;
        }

        // 페이징이 다시 고장나면 중복 제거에 가려지지 않도록 페이지별 신규 건수를 남긴다.
        int collectedBeforePage = postings.size();

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

          postings.add(
              new CrawledJobItem(
                  "JOBKOREA",
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

        System.out.println(
            "[JobKoreaCrawler] Page "
                + page
                + ": rows="
                + rows.size()
                + ", newly collected="
                + (postings.size() - collectedBeforePage));

        // 서버 트래픽 방지 딜레이
        Thread.sleep(1000);

      } catch (Exception e) {
        System.err.println("[JobKoreaCrawler] Error on page " + page + ": " + e.getMessage());
      }
    }

    List<CrawledJobItem> enriched = new ArrayList<>();
    for (CrawledJobItem posting : postings) {
      enriched.add(enrichWithDetail(posting, rawWriter));
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    postings = enriched;

    System.out.println("[JobKoreaCrawler] Crawling finished. Total collected: " + postings.size());
    return postings;
  }

  private CrawledJobItem enrichWithDetail(CrawledJobItem posting, RawIngestWriter rawWriter) {
    try {
      Document detailDoc =
          Jsoup.connect(posting.link())
              .userAgent(
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
              .timeout(10000)
              .get();
      String detailHtml = detailDoc.outerHtml();

      rawWriter.append(
          new RawIngestItem("JOBKOREA", "detail-" + posting.id(), detailHtml, Instant.now()));

      List<String> skills = detailParser.extractSkills(detailHtml);
      String salaryRaw = detailParser.extractSalaryRaw(detailHtml);

      return new CrawledJobItem(
          posting.portal(),
          posting.id(),
          posting.companyName(),
          posting.title(),
          posting.careerType(),
          posting.careerRange(),
          posting.education(),
          posting.location(),
          posting.link(),
          posting.createdAt(),
          skills,
          salaryRaw);
    } catch (Exception e) {
      System.err.println(
          "[JobKoreaCrawler] Failed to fetch detail for " + posting.id() + ": " + e.getMessage());
      return posting;
    }
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
