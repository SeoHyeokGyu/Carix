package com.carix.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
  void excludesSoftSkillsFromTheSkillList() throws IOException {
    String html = loadFixture();

    List<String> skills = new JobKoreaDetailParser().extractSkills(html);

    // 잡코리아는 인성 역량도 같은 배열에 담아 준다. 스킬 수요 집계에 섞이면 안 된다.
    assertFalse(skills.contains("성실성"));
    assertFalse(skills.contains("협동심"));
    assertFalse(skills.contains("성취지향성"));
  }

  @Test
  void extractsJobTypeName() throws IOException {
    String html = loadFixture();

    assertEquals("백엔드개발자,프론트엔드개발자", new JobKoreaDetailParser().extractJobTypeName(html));
  }

  @Test
  void extractsCompanyScaleFields() throws IOException {
    String html = loadFixture();
    JobKoreaDetailParser parser = new JobKoreaDetailParser();

    assertEquals(29, parser.extractEmployeeCount(html));
    assertEquals("벤처기업", parser.extractCompanyTypeName(html));
  }

  @Test
  void returnsNullCompanyScaleWhenPayloadHasNoSuchFields() {
    JobKoreaDetailParser parser = new JobKoreaDetailParser();

    assertNull(parser.extractEmployeeCount("<html></html>"));
    assertNull(parser.extractCompanyTypeName("<html></html>"));
  }

  @Test
  void extractsSalaryRawJsonFromEmbeddedPayload() throws IOException {
    String html = loadFixture();

    String salaryRaw = new JobKoreaDetailParser().extractSalaryRaw(html);

    assertTrue(salaryRaw.contains("COMPANY_POLICY"));
  }
}
