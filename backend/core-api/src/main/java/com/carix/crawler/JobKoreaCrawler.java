package com.carix.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class JobKoreaCrawler {

  private final ObjectMapper objectMapper = new ObjectMapper();

  public List<JobPosting> crawl(String keyword, int page) {
    List<JobPosting> postings = new ArrayList<>();
    try {
      String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
      String url = String.format(
          "https://www.jobkorea.co.kr/Search/?stext=%s&tabType=recruit&Page_No=%d",
          encodedKeyword, page);

      System.out.println("[JobKoreaCrawler] Fetching page " + page + " : " + url);

      Document doc = Jsoup.connect(url)
          .userAgent(
              "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
          .header("Accept",
              "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
          .header("Accept-Language", "ko-KR,ko;q=0.8,en-US;q=0.5,en;q=0.3")
          .timeout(10000)
          .get();

      // Next.js App Router Payload 스크립트 결합
      Elements scripts = doc.select("script");
      StringBuilder payloadBuilder = new StringBuilder();
      for (Element script : scripts) {
        String scriptHtml = script.html();
        if (scriptHtml.contains("self.__next_f.push")) {
          payloadBuilder.append(scriptHtml).append("\n");
        }
      }
      String payload = payloadBuilder.toString();

      // Bracket matching을 사용하여 content 리스트 추출
      // 백슬래시와 따옴표 이스케이프 개수에 대응하기 위한 정확한 이스케이프 탐색
      String target = "content\\\":[";
      int idx = payload.indexOf(target);
      java.util.Set<String> collectedIds = new java.util.HashSet<>();

      while (idx != -1) {
        int bracketStart = idx + target.length() - 1;
        int braceCount = 0;
        int endIdx = -1;

        for (int i = bracketStart; i < payload.length(); i++) {
          char c = payload.charAt(i);
          if (c == '[') {
            braceCount++;
          } else if (c == ']') {
            braceCount--;
            if (braceCount == 0) {
              endIdx = i;
              break;
            }
          }
        }

        if (endIdx != -1) {
          String rawContent = payload.substring(bracketStart, endIdx + 1);

          // 채용공고의 필수 필드인 "legacyJobNo" 또는 "postingCompanyName"이 포함되어 있는지 확인
          if (rawContent.contains("companyName") && rawContent.contains("legacyJobNo")) {

            // JSON 표준 포맷에 맞게 백슬래시 이스케이프 해제
            String jsonArrayStr = rawContent
                .replace("\\\\\\\"", "\"")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");

            try {
              JsonNode rootArray = objectMapper.readTree(jsonArrayStr);
              if (rootArray.isArray()) {
                for (JsonNode node : rootArray) {
                  // 유효한 공고 데이터인지 확인
                  if (!node.has("id") || !node.has("companyName")) {
                    continue;
                  }

                  String id = node.path("id").asText("");
                  if (collectedIds.contains(id)) {
                    continue;
                  }
                  collectedIds.add(id);

                  String companyName = node.path("companyName").asText("");
                  String title = node.path("title").asText("");

                  // 경력 조건
                  String rawCareerType = node.path("careerType").asText("");
                  String careerType = "무관";
                  if ("1".equals(rawCareerType)) {
                    careerType = "신입";
                  } else if ("2".equals(rawCareerType)) {
                    careerType = "경력";
                  }
                  int careerRange = node.path("careerRange").asInt(0);

                  // 학력
                  String eduCode = node.path("educationCode").asText("");
                  String education = mapEduCode(eduCode);

                  // 근무지
                  String location = "전국";
                  JsonNode areaCodes = node.path("areaCodeList");
                  if (areaCodes.isArray() && areaCodes.size() > 0) {
                    location = areaCodes.get(0).asText("");
                  }

                  String link = "https://www.jobkorea.co.kr/Recruit/GI_Read/" + id;
                  String createdAt = node.path("createdAt").asText("");

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
              }
            } catch (Exception jsonEx) {
              System.err.println("[JobKoreaCrawler] JSON Parsing Error: " + jsonEx.getMessage());
            }
          }
        }

        idx = payload.indexOf(target, idx + 1);
      }

      if (postings.isEmpty()) {
        System.out.println("[JobKoreaCrawler] Could not find content marker in payload.");
      }

    } catch (IOException e) {
      System.err.println("[JobKoreaCrawler] IO Error: " + e.getMessage());
    }
    return postings;
  }

  private String mapEduCode(String code) {
    return switch (code) {
      case "1" -> "고졸";
      case "3" -> "초대졸";
      case "5" -> "대졸(4년)";
      case "7" -> "석사";
      case "9" -> "박사";
      default -> "학력무관";
    };
  }
}
