package com.carix.crawler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobKoreaDetailParserTest {

  private String loadFixture() throws IOException {
    Path path = Path.of("src/test/resources/crawler/jobkorea_detail_sample.html");
    return Files.readString(path);
  }

  @Test
  void extractsHardSkillNamesFromEmbeddedPayload() throws IOException {
    String html = loadFixture();

    List<String> skills = new JobKoreaDetailParser().extractSkills(html);

    assertTrue(skills.contains("AWS"));
    assertTrue(skills.contains("Docker"));
    assertTrue(skills.contains("Python"));
  }

  @Test
  void extractsSalaryRawJsonFromEmbeddedPayload() throws IOException {
    String html = loadFixture();

    String salaryRaw = new JobKoreaDetailParser().extractSalaryRaw(html);

    assertTrue(salaryRaw.contains("COMPANY_POLICY"));
  }
}
