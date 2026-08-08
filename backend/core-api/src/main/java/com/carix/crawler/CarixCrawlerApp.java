package com.carix.crawler;

import java.io.File;
import java.util.List;

public class CarixCrawlerApp {

  public static void main(String[] args) {
    System.out.println("==================================================");
    System.out.println("            Carix Job Portal Crawler              ");
    System.out.println("==================================================");

    String outputDir = resolveOutputDir();

    // 1. 잡코리아 크롤링 진행
    crawlJobKorea("IT개발", 10, outputDir);

    System.out.println("--------------------------------------------------");

    // 2. 사람인 크롤링 진행
    crawlSaramin(10, outputDir);

    System.out.println("==================================================");
    System.out.println("         Crawler Execution Completed              ");
    System.out.println("==================================================");
  }

  public static void crawlJobKorea(String keyword, int pageLimit, String outputDir) {
    JobKoreaCrawler jobKoreaCrawler = new JobKoreaCrawler();
    System.out.println("[Step 1] JobKorea Crawling Start...");
    List<JobPosting> jkPostings = jobKoreaCrawler.crawl(keyword, pageLimit);
    System.out.println(
        "[Step 1] JobKorea Crawling Finished. Extracted: " + jkPostings.size() + " items");

    if (!jkPostings.isEmpty()) {
      String jkCsvPath = outputDir + "/jobkorea_jobs.csv";
      CsvExporter.exportToCsv(jkPostings, jkCsvPath);
    } else {
      System.out.println("[JobKorea] No postings crawled, skipping CSV export.");
    }
  }

  public static void crawlSaramin(int pageLimit, String outputDir) {
    SaraminCrawler saraminCrawler = new SaraminCrawler();
    System.out.println("[Step 2] Saramin Crawling Start...");
    List<JobPosting> saraminPostings = saraminCrawler.crawl(pageLimit, outputDir);
    System.out.println(
        "[Step 2] Saramin Crawling Finished. Extracted: " + saraminPostings.size() + " items");

    if (!saraminPostings.isEmpty()) {
      String saraminCsvPath = outputDir + "/saramin_jobs.csv";
      CsvExporter.exportToCsv(saraminPostings, saraminCsvPath);
    } else {
      System.out.println("[Saramin] No postings crawled, skipping CSV export.");
    }
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
