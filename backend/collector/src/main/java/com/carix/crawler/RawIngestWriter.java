package com.carix.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RawIngestWriter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  public static void write(RawIngest rawIngest, String outputDir) {
    Path dir = Path.of(outputDir, "raw", rawIngest.source());
    try {
      Files.createDirectories(dir);
      Path file = dir.resolve(rawIngest.rawId() + ".json");
      Files.writeString(file, OBJECT_MAPPER.writeValueAsString(rawIngest));
    } catch (IOException e) {
      System.err.println("[RawIngestWriter] Failed to write raw ingest for "
          + rawIngest.source() + "/" + rawIngest.rawId() + ": " + e.getMessage());
    }
  }
}
