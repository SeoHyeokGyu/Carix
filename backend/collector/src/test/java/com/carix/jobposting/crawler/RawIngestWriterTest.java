package com.carix.jobposting.crawler;

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
    RawIngestItem rawIngest =
        new RawIngestItem("JOBKOREA", "49731875", "<html>raw</html>", collectedAt);

    RawIngestWriter writer = new RawIngestWriter(tempDir);
    Path writtenFile = writer.append(rawIngest);

    assertTrue(Files.exists(writtenFile));

    String content = Files.readString(writtenFile);
    JsonNode node = new ObjectMapper().readTree(content);
    assertEquals("JOBKOREA", node.path("source").asText());
    assertEquals("49731875", node.path("rawId").asText());
    assertEquals("<html>raw</html>", node.path("rawContent").asText());
    assertEquals("2026-08-08T00:00:00Z", node.path("collectedAt").asText());
  }
}
