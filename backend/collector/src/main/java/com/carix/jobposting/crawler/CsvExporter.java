package com.carix.jobposting.crawler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** 수집된 채용공고 목록을 CSV 포맷 파일로 내보내는 유틸리티 클래스입니다. */
public class CsvExporter {

  private static final String CSV_HEADER =
      "Portal,ID,CompanyName,Title,CareerType,CareerRange,Education,Location,Link,CreatedAt,Skills\n";

  public static void exportToCsv(List<CrawledJobItem> postings, String filePath)
      throws IOException {
    Path path = Path.of(filePath);

    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }

    try (BufferedWriter writer =
        Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING)) {
      writer.write('\ufeff'); // UTF-8 BOM for Excel
      writer.write(CSV_HEADER);

      for (CrawledJobItem post : postings) {
        String skillsFormatted = post.skills() != null ? String.join(";", post.skills()) : "";

        String line =
            String.format(
                "\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                escapeCsv(post.portal()),
                escapeCsv(post.id()),
                escapeCsv(post.companyName()),
                escapeCsv(post.title()),
                escapeCsv(post.careerType()),
                post.careerRange(),
                escapeCsv(post.education()),
                escapeCsv(post.location()),
                escapeCsv(post.link()),
                escapeCsv(post.createdAt()),
                escapeCsv(skillsFormatted));
        writer.write(line);
      }
    }
  }

  private static String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\"", "\"\"");
  }
}
