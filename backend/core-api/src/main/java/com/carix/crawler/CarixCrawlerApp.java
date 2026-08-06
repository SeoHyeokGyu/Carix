package com.carix.crawler;

import java.io.File;
import java.util.List;

public class CarixCrawlerApp {

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("            Carix Job Portal Crawler              ");
    System.out.println("==================================================");

    // 1. 잡코리아 크롤링 진행
    JobKoreaCrawler jobKoreaCrawler = new JobKoreaCrawler();
    String jobKoreaKeyword = "IT개발"; // 명목상 키워드
    int jobKoreaPageLimit = 3; // 수집 범위 확장 (3페이지 = 최대 120개)

    System.out.println("[Step 1] JobKorea Crawling Start...");
    List<JobPosting> jkPostings = jobKoreaCrawler.crawl(jobKoreaKeyword, jobKoreaPageLimit);
    System.out.println(
        "[Step 1] JobKorea Crawling Finished. Extracted: " + jkPostings.size() + " items");

    // CSV 저장 경로 상대 탐색
    String outputDir = resolveOutputDir();

    if (!jkPostings.isEmpty()) {
      String jkCsvPath = outputDir + "/jobkorea_jobs.csv";
      CsvExporter.exportToCsv(jkPostings, jkCsvPath);
    } else {
      System.out.println("[JobKorea] No postings crawled, skipping CSV export.");
    }

    System.out.println("--------------------------------------------------");

    // 2. 사람인 크롤링 진행
    SaraminCrawler saraminCrawler = new SaraminCrawler();
    int saraminPageLimit = 15; // 수집 범위 확장 (15페이지 수집)

    System.out.println("[Step 2] Saramin Crawling Start...");
    List<JobPosting> saraminPostings = saraminCrawler.crawl(saraminPageLimit);
    System.out.println(
        "[Step 2] Saramin Crawling Finished. Extracted: " + saraminPostings.size() + " items");

    if (!saraminPostings.isEmpty()) {
      String saraminCsvPath = outputDir + "/saramin_jobs.csv";
      CsvExporter.exportToCsv(saraminPostings, saraminCsvPath);
    } else {
      System.out.println("[Saramin] No postings crawled, skipping CSV export.");
    }

    System.out.println("==================================================");
    System.out.println("         Crawler Execution Completed              ");
    System.out.println("==================================================");
  }

  private static String resolveOutputDir() {
    File currentDir = new File(System.getProperty("user.dir"));
    File tempDir = currentDir;

    // 부모 디렉토리를 거슬러 올라가며 Carix 프로젝트 루트 디렉토리를 탐색합니다.
    while (tempDir != null && !tempDir.getName().equals("Carix")) {
      tempDir = tempDir.getParentFile();
    }

    String rootPath = (tempDir != null) ? tempDir.getAbsolutePath() : currentDir.getAbsolutePath();
    return rootPath + "/verification/out";
  }
}
