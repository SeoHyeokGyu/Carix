package com.carix.crawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawIngestWriterTest {

  @Test
  void writesRawContentAsJsonFileUnderSourceDirectory(@TempDir Path tempDir) throws IOException {
    Instant collectedAt = Instant.parse("2026-08-08T00:00:00Z");
    RawIngest rawIngest = new RawIngest("JOBKOREA", "49731875", "<html>raw</html>", collectedAt);

    RawIngestWriter.write(rawIngest, tempDir.toString());

    Path expected = tempDir.resolve("raw").resolve("JOBKOREA").resolve("49731875.json");
    assertTrue(Files.exists(expected));

    JsonNode node = new ObjectMapper().readTree(Files.readString(expected));
    assertEquals("JOBKOREA", node.path("source").asText());
    assertEquals("49731875", node.path("rawId").asText());
    assertEquals("<html>raw</html>", node.path("rawContent").asText());
    assertEquals("2026-08-08T00:00:00Z", node.path("collectedAt").asText());
  }
}
