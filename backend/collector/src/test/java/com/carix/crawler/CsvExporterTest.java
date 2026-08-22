package com.carix.crawler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvExporterTest {

  @Test
  void exportsSkillsAndSalaryColumns(@TempDir Path tempDir) throws IOException {
    JobPosting posting =
        new JobPosting(
            "JOBKOREA",
            "123",
            "Acme",
            "Backend Engineer",
            "신입",
            0,
            "대졸↑",
            "서울",
            "https://example.com/123",
            "2026-08-08",
            List.of("Java", "Spring"),
            "{\"payType\":\"COMPANY_POLICY\"}");

    Path csvPath = tempDir.resolve("out.csv");
    CsvExporter.exportToCsv(List.of(posting), csvPath.toString());

    String content = Files.readString(csvPath);
    assertTrue(content.contains("Skills"));
    assertTrue(content.contains("SalaryRaw"));
    assertTrue(content.contains("Java;Spring"));
    assertTrue(content.contains("COMPANY_POLICY"));
  }
}
