package com.carix.crawler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public class CsvExporter {

  public static void exportToCsv(List<JobPosting> postings, String filePath) {
    System.out.println("[CsvExporter] Saving " + postings.size() + " postings to " + filePath);

    File file = new File(filePath);
    File parentDir = file.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      parentDir.mkdirs();
    }

    // UTF-8 with BOM (\uFEFF)을 지원하여 엑셀에서 한글이 깨지지 않게 처리
    try (FileOutputStream fos = new FileOutputStream(file);
        OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
        BufferedWriter writer = new BufferedWriter(osw)) {

      // BOM 문자 쓰기
      writer.write('\uFEFF');

      CSVFormat format = CSVFormat.DEFAULT.builder()
          .setHeader("Portal", "ID", "Company", "Title", "CareerType", "CareerRange", "Education",
              "Location", "Link", "CreatedAt", "Skills", "SalaryRaw")
          .build();

      try (CSVPrinter csvPrinter = new CSVPrinter(writer, format)) {
        for (JobPosting posting : postings) {
          csvPrinter.printRecord(
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
              String.join(";", posting.skills()),
              posting.salaryRaw()
          );
        }
        csvPrinter.flush();
        System.out.println("[CsvExporter] Successfully exported to CSV: " + file.getAbsolutePath());
      }

    } catch (Exception e) {
      System.err.println("[CsvExporter] Failed to export CSV: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
