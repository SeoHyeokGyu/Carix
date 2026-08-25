package com.carix.jobposting.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/** RawIngest 규약(출처, 원문 ID, 원문, 수집 시각)에 맞춰 raw_ingest-{source}-{date}.jsonl 파일로 원문을 보존하는 라이터. */
public class RawIngestWriter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final Path outputDir;

  public RawIngestWriter(Path outputDir) {
    this.outputDir = outputDir;
  }

  public Path append(RawIngestItem item) throws IOException {
    Files.createDirectories(outputDir);

    String dateStr = LocalDate.now().format(DATE_FORMAT);
    String fileName = String.format("raw_ingest-%s-%s.jsonl", item.source().toLowerCase(), dateStr);
    Path targetFile = outputDir.resolve(fileName);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("source", item.source());
    payload.put("rawId", item.rawId());
    payload.put("rawContent", item.rawContent());
    payload.put("collectedAt", item.collectedAt().toString());

    String line = OBJECT_MAPPER.writeValueAsString(payload);

    try (BufferedWriter writer =
        Files.newBufferedWriter(
            targetFile,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND)) {
      writer.write(line);
      writer.newLine();
    }

    return targetFile;
  }
}
