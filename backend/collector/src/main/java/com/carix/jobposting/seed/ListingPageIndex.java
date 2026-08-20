package com.carix.jobposting.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 * verification/out/raw/{JOBKOREA,SARAMIN}/listpage-*.json 전체를 한 번 읽어 인덱싱한다.
 * 사람인은 상세페이지 raw가 없으므로(design 문서 §4) collected_at·raw_ingest 연결이
 * 전부 이 listpage 인덱스에서 나온다 — 그래서 사람인 쪽은 html뿐 아니라 어느 파일
 * (sourceItemId)에서 왔는지, 그 파일의 collectedAt까지 같이 들고 있는다.
 */
public class ListingPageIndex {

  public record ListpageEntry(String sourceItemId, String html, Instant collectedAt) {

  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final Map<String, String> jobKoreaIdToHtml = new HashMap<>();
  private final Map<String, ListpageEntry> saraminIdToListpage = new HashMap<>();

  public void build(Path rawDir) throws IOException {
    indexJobKorea(rawDir.resolve("JOBKOREA"));
    indexSaramin(rawDir.resolve("SARAMIN"));
  }

  private void indexJobKorea(Path sourceDir) throws IOException {
    if (!Files.isDirectory(sourceDir)) {
      return;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir, "listpage-*.json")) {
      for (Path file : stream) {
        JsonNode node = OBJECT_MAPPER.readTree(file.toFile());
        String html = node.path("rawContent").asText();
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("tr.devloopArea[data-gno]");
        for (var row : rows) {
          String id = row.attr("data-gno");
          if (!id.isEmpty()) {
            jobKoreaIdToHtml.put(id, html);
          }
        }
      }
    }
  }

  private void indexSaramin(Path sourceDir) throws IOException {
    if (!Files.isDirectory(sourceDir)) {
      return;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir, "listpage-*.json")) {
      for (Path file : stream) {
        JsonNode node = OBJECT_MAPPER.readTree(file.toFile());
        String html = node.path("rawContent").asText();
        String sourceItemId = node.path("rawId").asText();
        Instant collectedAt = Instant.parse(node.path("collectedAt").asText());
        ListpageEntry entry = new ListpageEntry(sourceItemId, html, collectedAt);

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("div.item_recruit[value]");
        for (var item : items) {
          String id = item.attr("value");
          if (!id.isEmpty()) {
            saraminIdToListpage.put(id, entry);
          }
        }
      }
    }
  }

  public String htmlForJobKorea(String externalId) {
    return jobKoreaIdToHtml.get(externalId);
  }

  public ListpageEntry listpageForSaramin(String externalId) {
    return saraminIdToListpage.get(externalId);
  }
}
