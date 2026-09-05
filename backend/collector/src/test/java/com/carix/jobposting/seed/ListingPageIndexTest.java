package com.carix.jobposting.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ListingPageIndexTest {

  @TempDir Path rawDir;

  @Test
  @DisplayName("build - 잡코리아 및 사람인 목록 JSON 인덱싱")
  void build_indexesBothPortals() throws IOException {
    Path jkDir = rawDir.resolve("JOBKOREA");
    Path smDir = rawDir.resolve("SARAMIN");
    Files.createDirectories(jkDir);
    Files.createDirectories(smDir);

    String jkJson =
        """
        {
          "rawId": "listpage-1",
          "rawContent": "<table><tr class=\\"devloopArea\\" data-gno=\\"1001\\"></tr></table>"
        }
        """;
    Files.writeString(jkDir.resolve("listpage-1.json"), jkJson, StandardCharsets.UTF_8);

    String smJson =
        """
        {
          "rawId": "listpage-1",
          "rawContent": "<div class=\\"item_recruit\\" value=\\"2001\\"></div>",
          "collectedAt": "2026-08-01T00:00:00Z"
        }
        """;
    Files.writeString(smDir.resolve("listpage-1.json"), smJson, StandardCharsets.UTF_8);

    ListingPageIndex index = new ListingPageIndex();
    index.build(rawDir);

    assertThat(index.htmlForJobKorea("1001")).contains("1001");
    assertThat(index.htmlForJobKorea("unknown")).isNull();

    var smEntry = index.listpageForSaramin("2001");
    assertThat(smEntry).isNotNull();
    assertThat(smEntry.sourceItemId()).isEqualTo("listpage-1");
    assertThat(index.listpageForSaramin("unknown")).isNull();
  }

  @Test
  @DisplayName("build - 디렉토리가 없어도 안전하게 무시")
  void build_noDirectories() throws IOException {
    ListingPageIndex index = new ListingPageIndex();
    index.build(rawDir.resolve("non-existent"));

    assertThat(index.htmlForJobKorea("1001")).isNull();
    assertThat(index.listpageForSaramin("2001")).isNull();
  }
}
